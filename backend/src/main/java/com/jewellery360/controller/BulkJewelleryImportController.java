package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/domain/jewellery/import")
@RequiredArgsConstructor
public class BulkJewelleryImportController {

    private final ObjectMapper mapper;
    private final PermissionService permissions;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final JewelleryCategoryRepository categories;
    private final JewelleryDesignRepository designs;
    private final JewelleryProductRepository products;
    private final JewelleryTagRepository tags;
    private final JewelleryItemRepository items;
    private final PurityMasterRepository purities;
    private final StockRepository stock;
    private final HttpServletRequest request;

    @GetMapping("/template")
    public org.springframework.http.ResponseEntity<String> template() {
        String csv = """
Product Name,SKU,Category,Design,Purity,Tag No,Barcode,Gross Weight,Stone Weight,Net Weight,HUID,Wastage %,Making Charge,Stone Value
Gold Ring,,Rings,,22K,,,"4.250","0.000","4.250",,0,850,0
Gold Chain,,Chains,,22K,,,"18.500","0.000","18.500",,0,1200,0
""";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"jewellery-product-import-template.csv\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csv);
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestBody JsonNode body) {
        permissions.requireWrite(me, "JEWELLERY");
        Context ctx = context(me);
        List<Row> rows = parseRows(body);
        ValidationResult result = validate(ctx, rows);
        return result.toMap();
    }

    @PostMapping("/commit")
    @Transactional
    public Map<String, Object> commit(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestBody JsonNode body) {
        permissions.requireWrite(me, "JEWELLERY");
        Context ctx = context(me);
        List<Row> rows = parseRows(body);
        ValidationResult validation = validate(ctx, rows);
        if (validation.errorCount > 0) {
            throw bad("Import contains " + validation.errorCount + " error(s). Fix the highlighted rows and import again.");
        }

        // Refresh master data inside the transaction so this remains safe if another
        // user creates a matching master between preview and commit.
        Map<String, JewelleryCategory> categoryMap = categoryMap(ctx.company.getId());
        Map<String, JewelleryDesign> designMap = designMap(ctx.company.getId());
        Map<String, JewelleryProduct> productMap = productMap(ctx.company.getId());
        Map<String, JewelleryTag> tagMap = tagMap(ctx.company.getId());
        Map<String, PurityMaster> purityMap = purityMap(ctx.company.getId());
        Map<Long, JewelleryItem> itemByTagId = new HashMap<>();
        for (JewelleryItem i : items.findByCompanyId(ctx.company.getId())) {
            if (i.getTag() != null) itemByTagId.put(i.getTag().getId(), i);
        }

        int created = 0;
        int skipped = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (Row r : rows) {
            JewelleryCategory category = categoryMap.get(norm(r.category));
            if (category == null) {
                category = new JewelleryCategory();
                category.setCompany(ctx.company);
                category.setName(r.category.trim());
                category.setCode(generatedCode("CAT", r.category));
                category.setActive(true);
                category = categories.save(category);
                categoryMap.put(norm(r.category), category);
            }

            JewelleryDesign design = null;
            if (!blank(r.design)) {
                String dk = designKey(r.category, r.design);
                design = designMap.get(dk);
                if (design == null) {
                    design = new JewelleryDesign();
                    design.setCompany(ctx.company);
                    design.setCategory(category);
                    design.setName(r.design.trim());
                    design.setCode(generatedCode("DES", r.category + "|" + r.design));
                    design.setActive(true);
                    design = designs.save(design);
                    designMap.put(dk, design);
                }
            }

            String sku = blank(r.sku) ? generatedSku(r, r.rowNumber) : r.sku.trim();
            JewelleryProduct product = productMap.get(norm(sku));
            if (product == null) {
                product = new JewelleryProduct();
                product.setCompany(ctx.company);
                product.setCategory(category);
                product.setDesign(design);
                product.setName(r.productName.trim());
                product.setSku(sku);
                product.setDescription(blank(r.description) ? null : r.description.trim());
                product.setActive(true);
                product = products.save(product);
                productMap.put(norm(sku), product);
            } else {
                if (product.getCategory() != null && !Objects.equals(product.getCategory().getId(), category.getId())) {
                    throw bad("Row " + r.rowNumber + ": SKU " + sku + " already belongs to another category.");
                }
                if (design != null && product.getDesign() == null) {
                    product.setDesign(design);
                    product = products.save(product);
                }
            }

            String tagNo = blank(r.tagNo) ? generatedTag(r, r.rowNumber) : r.tagNo.trim();
            String barcode = blank(r.barcode) ? tagNo : r.barcode.trim();

            JewelleryTag tag = tagMap.get(norm(barcode));
            if (tag == null) tag = tagMap.get("TAG:" + norm(tagNo));

            if (tag != null) {
                if (!Objects.equals(tag.getBranch().getId(), ctx.branch.getId())) {
                    throw bad("Row " + r.rowNumber + ": tag/barcode already belongs to another branch.");
                }
                JewelleryItem existingItem = itemByTagId.get(tag.getId());
                if (existingItem != null) {
                    skipped++;
                    results.add(resultRow(r.rowNumber, "SKIPPED", "Existing tag/item already present."));
                    continue;
                }
            } else {
                tag = new JewelleryTag();
                tag.setCompany(ctx.company);
                tag.setBranch(ctx.branch);
                tag.setTagNo(tagNo);
                tag.setBarcode(barcode);
                tag.setStatus("AVAILABLE");
                tag = tags.save(tag);
                tagMap.put(norm(barcode), tag);
                tagMap.put("TAG:" + norm(tagNo), tag);
            }

            PurityMaster purity = purityMap.get(norm(r.purity));
            if (purity == null) {
                throw bad("Row " + r.rowNumber + ": purity '" + r.purity + "' is not configured in Purity Master.");
            }

            JewelleryItem item = new JewelleryItem();
            item.setCompany(ctx.company);
            item.setBranch(ctx.branch);
            item.setProduct(product);
            item.setTag(tag);
            item.setPurity(purity);
            item.setGrossWeight(r.grossWeight);
            item.setStoneWeight(r.stoneWeight);
            item.setNetWeight(r.netWeight != null ? r.netWeight : r.grossWeight.subtract(r.stoneWeight));
            item.setHuid(blank(r.huid) ? null : r.huid.trim());
            item.setWastagePercent(r.wastagePercent);
            item.setMakingCharge(r.makingCharge);
            item.setStoneValue(r.stoneValue);
            item.setStatus("IN_STOCK");
            item.setCreatedAt(Instant.now());
            item.setUpdatedAt(Instant.now());
            item = items.save(item);
            itemByTagId.put(tag.getId(), item);

            Stock stockRow = new Stock();
            stockRow.setCompany(ctx.company);
            stockRow.setBranch(ctx.branch);
            stockRow.setJewelleryItem(item);
            stockRow.setStatus("IN_STOCK");
            stockRow.setUpdatedAt(Instant.now());
            stock.save(stockRow);

            created++;
            results.add(resultRow(r.rowNumber, "IMPORTED", "Product and stock item created."));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("created", created);
        response.put("skipped", skipped);
        response.put("total", rows.size());
        response.put("results", results);
        return response;
    }

    private ValidationResult validate(Context ctx, List<Row> rows) {
        ValidationResult out = new ValidationResult();
        Map<String, JewelleryCategory> categoryMap = categoryMap(ctx.company.getId());
        Map<String, JewelleryProduct> productMap = productMap(ctx.company.getId());
        Map<String, JewelleryTag> tagMap = tagMap(ctx.company.getId());
        Map<String, PurityMaster> purityMap = purityMap(ctx.company.getId());

        if (rows.isEmpty()) {
            out.add(0, "EMPTY", "No product rows found.");
            return out;
        }
        if (rows.size() > 10000) {
            out.add(0, "LIMIT", "Maximum 10,000 rows per import.");
            return out;
        }

        Set<String> batchSkus = new HashSet<>();
        Set<String> batchTags = new HashSet<>();
        Set<String> batchBarcodes = new HashSet<>();

        for (Row r : rows) {
            List<String> errors = new ArrayList<>();
            if (blank(r.productName)) errors.add("Product Name is required.");
            if (blank(r.category)) errors.add("Category is required.");
            if (blank(r.purity)) errors.add("Purity is required.");
            if (r.grossWeight == null || r.grossWeight.compareTo(BigDecimal.ZERO) <= 0) errors.add("Gross Weight must be greater than 0.");
            if (r.stoneWeight.compareTo(r.grossWeight == null ? BigDecimal.ZERO : r.grossWeight) > 0) errors.add("Stone Weight cannot exceed Gross Weight.");

            String sku = blank(r.sku) ? generatedSku(r, r.rowNumber) : r.sku.trim();
            String tagNo = blank(r.tagNo) ? generatedTag(r, r.rowNumber) : r.tagNo.trim();
            String barcode = blank(r.barcode) ? tagNo : r.barcode.trim();

            if (!batchSkus.add(norm(sku)) && !blank(r.sku)) errors.add("Duplicate SKU in this file.");
            if (!batchTags.add(norm(tagNo)) && !blank(r.tagNo)) errors.add("Duplicate Tag No in this file.");
            if (!batchBarcodes.add(norm(barcode)) && !blank(r.barcode)) errors.add("Duplicate Barcode in this file.");

            if (!blank(r.purity) && !purityMap.containsKey(norm(r.purity)))
                errors.add("Purity '" + r.purity + "' is not configured in Purity Master.");

            JewelleryProduct existingProduct = productMap.get(norm(sku));
            if (existingProduct != null && !blank(r.category) && existingProduct.getCategory() != null &&
                    !norm(existingProduct.getCategory().getName()).equals(norm(r.category))) {
                errors.add("SKU already exists under category '" + existingProduct.getCategory().getName() + "'.");
            }

            JewelleryTag existingTag = tagMap.get(norm(barcode));
            if (existingTag == null) existingTag = tagMap.get("TAG:" + norm(tagNo));
            if (existingTag != null && existingTag.getBranch() != null &&
                    !Objects.equals(existingTag.getBranch().getId(), ctx.branch.getId())) {
                errors.add("Tag/barcode belongs to another branch.");
            }

            if (!errors.isEmpty()) {
                out.add(r.rowNumber, "ERROR", String.join(" ", errors));
            } else {
                out.add(r.rowNumber, "READY", "Ready to import.");
            }
        }
        return out;
    }

    private List<Row> parseRows(JsonNode body) {
        JsonNode rowsNode = body != null && body.isArray() ? body : body == null ? null : body.get("rows");
        if (rowsNode == null || !rowsNode.isArray()) throw bad("Import rows are required.");
        List<Row> rows = new ArrayList<>();
        int rowNo = 1;
        for (JsonNode n : rowsNode) {
            Row r = new Row();
            r.rowNumber = n.has("rowNumber") ? n.get("rowNumber").asInt(rowNo) : rowNo;
            r.productName = text(n, "productName", "name", "itemName");
            r.sku = text(n, "sku", "itemCode", "code");
            r.category = text(n, "category", "categoryName");
            r.design = text(n, "design", "designName");
            r.purity = text(n, "purity", "purityName");
            r.tagNo = text(n, "tagNo", "tag", "tagNumber");
            r.barcode = text(n, "barcode", "barCode");
            r.description = text(n, "description");
            r.huid = text(n, "huid");
            r.grossWeight = decimal(n, "grossWeight", "grossWeightG", "grossWt");
            r.stoneWeight = decimalOrZero(n, "stoneWeight", "stoneWeightG", "stoneWt");
            r.netWeight = decimal(n, "netWeight", "netWeightG", "netWt");
            r.wastagePercent = decimalOrZero(n, "wastagePercent", "wastage");
            r.makingCharge = decimalOrZero(n, "makingCharge", "making", "makingCharges");
            r.stoneValue = decimalOrZero(n, "stoneValue", "stoneAmount");
            rows.add(r);
            rowNo++;
        }
        return rows;
    }

    private Context context(AuthenticatedUser me) {
        Long companyId = me.getCompanyId();
        Long branchId = me.getBranchId();

        if ("APP_ADMIN".equals(me.getRole())) {
            companyId = longHeader("X-Company-Id");
            branchId = longHeader("X-Branch-Id");
        }
        if (companyId == null) throw bad("Company scope is required.");
        if (branchId == null) throw bad("Branch context is required for bulk jewellery import.");

        Company company = companies.findById(companyId).orElseThrow(() -> bad("Company not found."));
        Branch branch = branches.findById(branchId).orElseThrow(() -> bad("Branch not found."));
        if (!Objects.equals(branch.getCompany().getId(), company.getId())) throw bad("Branch does not belong to company.");
        if (!"APP_ADMIN".equals(me.getRole()) && !Objects.equals(me.getCompanyId(), company.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company access denied.");
        if (!"APP_ADMIN".equals(me.getRole()) && me.getBranchId() != null && !Objects.equals(me.getBranchId(), branch.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch access denied.");

        return new Context(company, branch);
    }

    private Long longHeader(String name) {
        String v = request.getHeader(name);
        if (v == null || v.isBlank()) throw bad("APP_ADMIN requires " + name + " context.");
        try { return Long.parseLong(v); } catch (NumberFormatException e) { throw bad("Invalid " + name + " context."); }
    }

    private Map<String, JewelleryCategory> categoryMap(Long companyId) {
        Map<String, JewelleryCategory> m = new HashMap<>();
        for (JewelleryCategory x : categories.findByCompanyId(companyId)) m.put(norm(x.getName()), x);
        return m;
    }

    private Map<String, JewelleryDesign> designMap(Long companyId) {
        Map<String, JewelleryDesign> m = new HashMap<>();
        for (JewelleryDesign x : designs.findByCompanyId(companyId))
            m.put(designKey(x.getCategory() == null ? "" : x.getCategory().getName(), x.getName()), x);
        return m;
    }

    private Map<String, JewelleryProduct> productMap(Long companyId) {
        Map<String, JewelleryProduct> m = new HashMap<>();
        for (JewelleryProduct x : products.findByCompanyId(companyId)) m.put(norm(x.getSku()), x);
        return m;
    }

    private Map<String, JewelleryTag> tagMap(Long companyId) {
        Map<String, JewelleryTag> m = new HashMap<>();
        for (JewelleryTag x : tags.findByCompanyId(companyId)) {
            if (x.getBarcode() != null) m.put(norm(x.getBarcode()), x);
            if (x.getTagNo() != null) m.put("TAG:" + norm(x.getTagNo()), x);
        }
        return m;
    }

    private Map<String, PurityMaster> purityMap(Long companyId) {
        Map<String, PurityMaster> m = new HashMap<>();
        for (PurityMaster x : purities.findByCompanyId(companyId)) m.put(norm(x.getName()), x);
        return m;
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull() && !v.asText().trim().isEmpty()) return v.asText().trim();
        }
        return "";
    }

    private static BigDecimal decimal(JsonNode n, String... keys) {
        String s = text(n, keys);
        if (s.isBlank()) return null;
        try { return new BigDecimal(s.replace(",", "").trim()); }
        catch (Exception e) { throw bad("Invalid number '" + s + "'."); }
    }

    private static BigDecimal decimalOrZero(JsonNode n, String... keys) {
        BigDecimal v = decimal(n, keys);
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String designKey(String category, String design) {
        return norm(category) + "|" + norm(design);
    }

    private static String generatedCode(String prefix, String value) {
        return prefix + "-" + hash(value, 10);
    }

    private static String generatedSku(Row r, int row) {
        return "IMP-" + hash((r.productName + "|" + r.category + "|" + r.design), 12);
    }

    private static String generatedTag(Row r, int row) {
        return "IMP-" + hash((r.productName + "|" + r.category + "|" + r.purity + "|" + row), 12);
    }

    private static String hash(String value, int length) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (byte x : b) s.append(String.format("%02x", x));
            return s.substring(0, Math.min(length, s.length())).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, length).toUpperCase(Locale.ROOT);
        }
    }

    private static Map<String, Object> resultRow(int row, String status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rowNumber", row);
        m.put("status", status);
        m.put("message", message);
        return m;
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static class Context {
        final Company company;
        final Branch branch;
        Context(Company company, Branch branch) { this.company = company; this.branch = branch; }
    }

    private static class Row {
        int rowNumber;
        String productName, sku, category, design, purity, tagNo, barcode, description, huid;
        BigDecimal grossWeight, stoneWeight = BigDecimal.ZERO, netWeight;
        BigDecimal wastagePercent = BigDecimal.ZERO, makingCharge = BigDecimal.ZERO, stoneValue = BigDecimal.ZERO;
    }

    private static class ValidationResult {
        int errorCount;
        final List<Map<String, Object>> results = new ArrayList<>();
        void add(int row, String status, String message) {
            if ("ERROR".equals(status)) errorCount++;
            results.add(resultRow(row, status, message));
        }
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", errorCount == 0);
            m.put("total", results.size());
            m.put("ready", results.stream().filter(x -> "READY".equals(x.get("status"))).count());
            m.put("errors", errorCount);
            m.put("results", results);
            return m;
        }
    }
}
