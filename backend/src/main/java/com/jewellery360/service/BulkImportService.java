package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Num;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Opening stock from a spreadsheet: preview every row first, then import all rows in one transaction (all or nothing). */
@Service
@RequiredArgsConstructor
public class BulkImportService {
    private final ObjectMapper mapper;
    private final JewelleryProductRepository products;
    private final JewelleryCategoryRepository categories;
    private final JewelleryDesignRepository designs;
    private final PurityMasterRepository purities;
    private final JewelleryItemRepository items;
    private final StockService stock;
    private final SkuService sku;
    private final PermissionService permissions;
    private final AuditService audit;

    private record Row(int rowNo, JsonNode raw, String productName, String sku, String category, String design, String purity, String tagNo,
                       String barcode, BigDecimal gross, BigDecimal stone, BigDecimal net) {}

    @Transactional(readOnly = true)
    public Map<String, Object> preview(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requireManage(ctx.me(), "STOCK");
        List<Row> rows = parse(body);
        return validate(ctx, rows);
    }

    @Transactional
    public Map<String, Object> commit(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requireManage(ctx.me(), "STOCK");
        List<Row> rows = parse(body);
        Map<String, Object> v = validate(ctx, rows);
        if ((int) v.get("errorCount") > 0) throw Errors.bad("The file has " + v.get("errorCount") + " row(s) with problems. Fix them and import again.");
        Map<String, PurityMaster> purityByName = purityMap(ctx);
        int created = 0;
        for (Row r : rows) {
            ObjectNode n = mapper.createObjectNode();
            n.put("productId", productFor(ctx, r).getId());
            n.put("purityId", purityByName.get(key(r.purity())).getId());
            n.put("grossWeight", r.gross());
            n.put("stoneWeight", r.stone());
            if (r.net() != null) n.put("netWeight", r.net());
            if (r.tagNo() != null) n.put("tagNo", r.tagNo());
            if (r.barcode() != null) n.put("barcode", r.barcode());
            copy(r.raw(), n, "huid", "huid");
            copy(r.raw(), n, "wastagePercent", "wastagePercent", "wastage");
            copy(r.raw(), n, "makingType", "makingType");
            copy(r.raw(), n, "makingValue", "makingValue", "makingCharge", "making");
            copy(r.raw(), n, "stoneValue", "stoneValue");
            copy(r.raw(), n, "costPrice", "costPrice", "cost");
            copy(r.raw(), n, "location", "location");
            n.put("source", "OPENING");
            stock.createImported(ctx, n);
            created++;
        }
        audit.log(ctx, "STOCK_IMPORT", "JewelleryItem", null, null, Map.of("pieces", created));
        return Map.of("success", true, "created", created, "total", rows.size());
    }

    private JewelleryProduct productFor(TenantContext.Ctx ctx, Row r) {
        if (r.sku() != null) {
            Optional<JewelleryProduct> bySku = products.findByCompanyIdAndSkuIgnoreCase(ctx.companyId(), r.sku());
            if (bySku.isPresent()) return bySku.get();
        } else {
            Optional<JewelleryProduct> byName = products.findFirstByCompanyIdAndNameIgnoreCase(ctx.companyId(), r.productName());
            if (byName.isPresent()) return byName.get();
        }
        JewelleryCategory category = categories.findFirstByCompanyIdAndNameIgnoreCase(ctx.companyId(), r.category()).orElseGet(() -> {
            JewelleryCategory c = new JewelleryCategory();
            c.setCompany(ctx.company());
            c.setName(r.category());
            c.setCode(MastersService.codeFrom(r.category(), x -> categories.findByCompanyIdAndCodeIgnoreCase(ctx.companyId(), x).isPresent()));
            return categories.save(c);
        });
        JewelleryDesign design = null;
        if (r.design() != null) design = designs.findFirstByCompanyIdAndNameIgnoreCase(ctx.companyId(), r.design()).orElseGet(() -> {
            JewelleryDesign d = new JewelleryDesign();
            d.setCompany(ctx.company());
            d.setCategory(category);
            d.setName(r.design());
            d.setCode(MastersService.codeFrom(r.design(), x -> designs.findByCompanyIdAndCodeIgnoreCase(ctx.companyId(), x).isPresent()));
            return designs.save(d);
        });
        JewelleryProduct p = new JewelleryProduct();
        p.setCompany(ctx.company());
        p.setCategory(category);
        p.setDesign(design);
        p.setName(r.productName());
        p.setSku(r.sku() != null ? r.sku().toUpperCase(Locale.ROOT) : sku.nextProductSku(ctx.company()));
        return products.saveAndFlush(p);
    }

    private Map<String, Object> validate(TenantContext.Ctx ctx, List<Row> rows) {
        Map<String, PurityMaster> purityByName = purityMap(ctx);
        Set<String> tags = new HashSet<>(), codes = new HashSet<>();
        List<Map<String, Object>> results = new ArrayList<>();
        int errors = 0;
        if (rows.isEmpty()) throw Errors.bad("No rows found in the file.");
        if (rows.size() > 10000) throw Errors.bad("Import at most 10,000 rows at a time.");
        for (Row r : rows) {
            List<String> e = new ArrayList<>();
            if (r.productName() == null) e.add("Product Name is required.");
            if (r.category() == null && r.sku() == null) e.add("Category is required.");
            if (r.purity() == null) e.add("Purity is required.");
            else if (!purityByName.containsKey(key(r.purity()))) e.add("Purity '" + r.purity() + "' is not set up (see Gold rates > Purities).");
            if (r.gross() == null || r.gross().signum() <= 0) e.add("Gross Weight must be more than 0.");
            else if (r.stone().compareTo(r.gross()) > 0) e.add("Stone Weight cannot be more than Gross Weight.");
            if (r.tagNo() != null) {
                if (!tags.add(key(r.tagNo()))) e.add("Tag No is repeated in the file.");
                else if (items.existsByCompanyIdAndTagNoIgnoreCase(ctx.companyId(), r.tagNo())) e.add("Tag No " + r.tagNo() + " already exists.");
            }
            if (r.barcode() != null) {
                if (!codes.add(key(r.barcode()))) e.add("Barcode is repeated in the file.");
                else if (items.existsByCompanyIdAndBarcode(ctx.companyId(), r.barcode())) e.add("Barcode " + r.barcode() + " already exists.");
            }
            if (!e.isEmpty()) errors++;
            results.add(Map.of("rowNumber", r.rowNo(), "status", e.isEmpty() ? "READY" : "ERROR", "message", e.isEmpty() ? "Ready to import." : String.join(" ", e)));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("readyCount", rows.size() - errors);
        out.put("errorCount", errors);
        out.put("rows", results);
        return out;
    }

    private Map<String, PurityMaster> purityMap(TenantContext.Ctx ctx) {
        Map<String, PurityMaster> m = new HashMap<>();
        for (PurityMaster p : purities.findByCompanyIdOrderByMetalAscFinenessDesc(ctx.companyId())) {
            if (!p.isActive()) continue;
            m.put(key(p.getName()), p);
            if (p.getKarat() != null) m.putIfAbsent(key(p.getKarat()), p);
        }
        return m;
    }

    private List<Row> parse(JsonNode body) {
        JsonNode list = body != null && body.isArray() ? body : body == null ? null : body.get("rows");
        if (list == null || !list.isArray()) throw Errors.bad("Import rows are required.");
        List<Row> rows = new ArrayList<>();
        int no = 1;
        for (JsonNode n : list) {
            int rowNo = n.has("rowNumber") ? n.get("rowNumber").asInt(no) : no;
            rows.add(new Row(rowNo, n, t(n, "productName", "name", "itemName"), t(n, "sku", "code"), t(n, "category", "categoryName"),
                    t(n, "design", "designName"), t(n, "purity", "purityName"), t(n, "tagNo", "tag"), t(n, "barcode"),
                    d(n, "grossWeight", "grossWt"), Optional.ofNullable(d(n, "stoneWeight", "stoneWt")).orElse(BigDecimal.ZERO), d(n, "netWeight", "netWt")));
            no++;
        }
        return rows;
    }

    private static String t(JsonNode n, String... names) {
        for (String f : names) { String v = Num.text(n, f); if (v != null) return v; }
        return null;
    }

    private static BigDecimal d(JsonNode n, String... names) {
        for (String f : names) { BigDecimal v = Num.decOrNull(n, f); if (v != null) return v; }
        return null;
    }

    private static void copy(JsonNode from, ObjectNode to, String target, String... names) {
        for (String f : names) {
            String v = Num.text(from, f);
            if (v != null) { to.put(target, v); return; }
        }
    }

    private static String key(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", ""); }
}
