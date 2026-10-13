package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Num;
import com.jewellery360.support.Views;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.jewellery360.support.Num.*;

/** Tagged pieces: add, edit, find (search / scan by barcode, tag or HUID), transfer, remove, and their full history. */
@Service
@RequiredArgsConstructor
public class StockService {
    private final JewelleryItemRepository items;
    private final JewelleryProductRepository products;
    private final JewelleryCategoryRepository categories;
    private final PurityMasterRepository purities;
    private final StockMovementRepository movements;
    private final StockTransferRepository transfers;
    private final StockTransferItemRepository transferItems;
    private final BranchRepository branches;
    private final SaleItemRepository saleItems;
    private final PurchaseItemRepository purchaseItems;
    private final SkuService sku;
    private final NumberingService numbering;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    private boolean cost(TenantContext.Ctx ctx) { return permissions.has(ctx.me(), "SEE_PROFIT"); }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(TenantContext.Ctx ctx, String status, String q, boolean allBranches, int limit) {
        if (!permissions.can(ctx.me(), "STOCK") && !permissions.can(ctx.me(), "BILLING")) permissions.requirePage(ctx.me(), "STOCK");
        Long branch = allBranches && ctx.admin() ? null : ctx.branchId();
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 2000)));
        List<JewelleryItem> rows = q == null || q.isBlank()
                ? items.list(ctx.companyId(), branch, status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(Locale.ROOT), page)
                : items.search(ctx.companyId(), branch, "%" + q.trim().toLowerCase(Locale.ROOT) + "%", page);
        boolean c = cost(ctx);
        return rows.stream().filter(i -> q == null || q.isBlank() || status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) || i.getStatus().equalsIgnoreCase(status))
                .map(i -> Views.item(i, c)).toList();
    }

    /** Barcode / tag / HUID lookup used by the billing counter and the scanner. Cashiers can scan without stock access. */
    @Transactional(readOnly = true)
    public Map<String, Object> scan(TenantContext.Ctx ctx, String code) {
        if (!permissions.can(ctx.me(), "BILLING")) permissions.requirePage(ctx.me(), "STOCK");
        String clean = code == null ? "" : code.trim();
        if (clean.isEmpty()) throw Errors.bad("Scan or type a barcode, tag number or HUID.");
        List<JewelleryItem> found = items.findByCode(ctx.companyId(), clean);
        if (found.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No piece with barcode, tag or HUID \"" + clean + "\".");
        JewelleryItem best = found.stream().filter(i -> JewelleryItem.IN_STOCK.equals(i.getStatus()) && Objects.equals(i.getBranch().getId(), ctx.branchId()))
                .findFirst().orElse(found.get(0));
        Map<String, Object> m = Views.item(best, cost(ctx));
        m.put("available", JewelleryItem.IN_STOCK.equals(best.getStatus()) && Objects.equals(best.getBranch().getId(), ctx.branchId()));
        m.put("message", !JewelleryItem.IN_STOCK.equals(best.getStatus()) ? "This piece is " + best.getStatus().replace('_', ' ').toLowerCase(Locale.ROOT) + "."
                : !Objects.equals(best.getBranch().getId(), ctx.branchId()) ? "This piece is in branch " + best.getBranch().getName() + "." : null);
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "STOCK");
        return Views.item(load(ctx, id), cost(ctx));
    }

    /**
     * Quick add: {productId | productName + categoryId|categoryName, purityId, grossWeight, stoneWeight, netWeight?, huid,
     * tagNo?, barcode?, wastagePercent, makingType, makingValue, stoneValue, stoneCount, stoneDetails, otherCharge, costPrice,
     * mrp, location, certificateNo, notes}. Tag number and barcode are numbered automatically when left empty.
     */
    @Transactional
    public Map<String, Object> create(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireManage(ctx.me(), "STOCK");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        JewelleryItem i = new JewelleryItem();
        i.setCompany(ctx.company());
        i.setBranch(ctx.branch());
        i.setSource(Optional.ofNullable(Num.text(n, "source")).filter(s -> List.of("OPENING", "PURCHASE", "OLD_GOLD", "CUSTOM_ORDER").contains(s)).orElse("OPENING"));
        apply(ctx, i, n, true);
        items.save(i);
        record(ctx, i, "OPENING".equals(i.getSource()) ? "OPENING" : i.getSource() + "_IN", null, null, "Added to stock");
        audit.log(ctx, "STOCK_ADD", "JewelleryItem", i.getId(), null, Map.of("tagNo", i.getTagNo(), "netWeight", i.getNetWeight(), "purity", i.getPurity().getName()));
        return Views.item(i, cost(ctx));
    }

    /** One imported row (permission and audit are handled once for the whole file). */
    JewelleryItem createImported(TenantContext.Ctx ctx, JsonNode n) {
        JewelleryItem i = new JewelleryItem();
        i.setCompany(ctx.company());
        i.setBranch(ctx.branch());
        i.setSource("OPENING");
        apply(ctx, i, n, true);
        items.saveAndFlush(i);
        record(ctx, i, "OPENING", null, null, "Imported");
        return i;
    }

    /** Creates a piece for a purchase (used by PurchaseService). */
    @Transactional
    public JewelleryItem createForPurchase(TenantContext.Ctx ctx, JsonNode n, PurchaseOrder purchase) {
        JewelleryItem i = new JewelleryItem();
        i.setCompany(ctx.company());
        i.setBranch(ctx.branch());
        i.setSource("PURCHASE");
        apply(ctx, i, n, true);
        items.save(i);
        record(ctx, i, "PURCHASE_IN", "PURCHASE", purchase.getId(), "Purchase " + purchase.getPurchaseNo());
        return i;
    }

    @Transactional
    public Map<String, Object> update(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireManage(ctx.me(), "STOCK");
        JewelleryItem i = items.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, i.getCompany(), i.getBranch());
        if (!JewelleryItem.IN_STOCK.equals(i.getStatus())) throw Errors.bad("Only pieces in stock can be edited; sold pieces keep the details they were billed with.");
        Map<String, Object> before = Views.item(i, true);
        apply(ctx, i, n, false);
        Map<String, Object> after = Views.item(i, true);
        record(ctx, i, "EDIT", null, null, "Details edited");
        audit.log(ctx, "STOCK_EDIT", "JewelleryItem", i.getId(), before, after);
        return Views.item(i, cost(ctx));
    }

    private void apply(TenantContext.Ctx ctx, JewelleryItem i, JsonNode n, boolean creating) {
        if (creating || n.has("productId") || n.has("productName")) i.setProduct(product(ctx, n));
        if (creating || n.has("purityId")) {
            PurityMaster p = purities.findById(Num.requireId(n, "purityId", "Purity")).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, p.getCompany());
            if (!p.isActive()) throw Errors.bad("Purity " + p.getName() + " is switched off.");
            i.setPurity(p);
        }
        if (creating || n.has("grossWeight")) i.setGrossWeight(r3(Num.dec(n, "grossWeight")));
        if (creating || n.has("stoneWeight")) i.setStoneWeight(r3(Num.nonNegative(Num.dec(n, "stoneWeight"), "Stone weight")));
        BigDecimal net = Num.decOrNull(n, "netWeight");
        if (net != null) i.setNetWeight(r3(net));
        else if (creating || n.has("grossWeight") || n.has("stoneWeight")) i.setNetWeight(r3(i.getGrossWeight().subtract(i.getStoneWeight())));
        if (i.getGrossWeight().signum() <= 0) throw Errors.bad("Gross weight must be more than 0.");
        if (i.getStoneWeight().compareTo(i.getGrossWeight()) > 0) throw Errors.bad("Stone weight cannot be more than the gross weight.");
        if (i.getNetWeight().signum() <= 0 || i.getNetWeight().compareTo(i.getGrossWeight()) > 0) throw Errors.bad("Net weight must be more than 0 and not more than the gross weight.");

        if (creating || n.has("tagNo")) {
            String tag = Num.text(n, "tagNo", 80);
            if (tag == null) tag = creating ? sku.nextTagNo(ctx.company()) : i.getTagNo();
            if (i.getId() == null ? items.existsByCompanyIdAndTagNoIgnoreCase(ctx.companyId(), tag) : items.existsByCompanyIdAndTagNoIgnoreCaseAndIdNot(ctx.companyId(), tag, i.getId()))
                throw Errors.conflict("Tag number " + tag + " is already used.");
            i.setTagNo(tag);
        }
        if (creating || n.has("barcode")) {
            String code = Num.text(n, "barcode", 100);
            if (code == null) code = i.getTagNo();
            if (i.getId() == null ? items.existsByCompanyIdAndBarcode(ctx.companyId(), code) : items.existsByCompanyIdAndBarcodeAndIdNot(ctx.companyId(), code, i.getId()))
                throw Errors.conflict("Barcode " + code + " is already used.");
            i.setBarcode(code);
        }
        if (creating || n.has("huid")) {
            String huid = Num.text(n, "huid", 20);
            if (huid != null) {
                huid = huid.toUpperCase(Locale.ROOT);
                if (!huid.matches("[A-Z0-9]{6}")) throw Errors.bad("HUID is the 6-character hallmark code (letters and digits).");
                for (JewelleryItem other : items.findByCode(ctx.companyId(), huid))
                    if (huid.equalsIgnoreCase(other.getHuid()) && !other.getId().equals(i.getId())) throw Errors.conflict("HUID " + huid + " is already on piece " + other.getTagNo() + ".");
            }
            i.setHuid(huid);
        }
        if (creating || n.has("certificateNo")) i.setCertificateNo(Num.text(n, "certificateNo", 80));
        if (creating || n.has("stoneCount")) i.setStoneCount(Math.max(0, n.path("stoneCount").asInt(0)));
        if (creating || n.has("stoneDetails")) i.setStoneDetails(Num.text(n, "stoneDetails", 500));
        if (creating || n.has("stoneValue")) i.setStoneValue(r2(Num.nonNegative(Num.dec(n, "stoneValue"), "Stone value")));
        if (creating || n.has("wastagePercent")) i.setWastagePercent(r3(Num.nonNegative(Num.dec(n, "wastagePercent"), "Wastage")));
        if (creating || n.has("makingType")) {
            String t = Optional.ofNullable(Num.text(n, "makingType")).orElse("FIXED").toUpperCase(Locale.ROOT);
            if (!PricingService.MAKING_TYPES.contains(t)) throw Errors.bad("Making charge type must be FIXED, PER_GRAM or PERCENT.");
            i.setMakingType(t);
        }
        if (creating || n.has("makingValue")) i.setMakingValue(r3(Num.nonNegative(Num.dec(n, "makingValue"), "Making charge")));
        if (creating || n.has("otherCharge")) i.setOtherCharge(r2(Num.nonNegative(Num.dec(n, "otherCharge"), "Other charge")));
        if (creating || n.has("costPrice")) i.setCostPrice(r2(Num.nonNegative(Num.dec(n, "costPrice"), "Cost price")));
        if (creating || n.has("mrp")) i.setMrp(r2(Num.nonNegative(Num.dec(n, "mrp"), "MRP")));
        if (creating || n.has("location")) i.setLocation(Num.text(n, "location", 120));
        if (creating || n.has("notes")) i.setNotes(Num.text(n, "notes", 1000));
    }

    /** Uses an existing product, or creates one on the fly so adding a piece never needs a separate master screen. */
    private JewelleryProduct product(TenantContext.Ctx ctx, JsonNode n) {
        Long id = Num.id(n, "productId");
        if (id != null) {
            JewelleryProduct p = products.findById(id).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, p.getCompany());
            return p;
        }
        String name = Num.text(n, "productName", 180);
        if (name == null) throw Errors.bad("Pick a product or type the product name.");
        Optional<JewelleryProduct> same = products.findFirstByCompanyIdAndNameIgnoreCase(ctx.companyId(), name);
        if (same.isPresent()) return same.get();
        JewelleryCategory category;
        Long categoryId = Num.id(n, "categoryId");
        if (categoryId != null) {
            category = categories.findById(categoryId).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, category.getCompany());
        } else {
            String catName = Optional.ofNullable(Num.text(n, "categoryName", 120)).orElse("General");
            category = categories.findFirstByCompanyIdAndNameIgnoreCase(ctx.companyId(), catName).orElseGet(() -> {
                JewelleryCategory c = new JewelleryCategory();
                c.setCompany(ctx.company());
                c.setName(catName);
                c.setCode(MastersService.codeFrom(catName, code -> categories.findByCompanyIdAndCodeIgnoreCase(ctx.companyId(), code).isPresent()));
                return categories.save(c);
            });
        }
        JewelleryProduct p = new JewelleryProduct();
        p.setCompany(ctx.company());
        p.setCategory(category);
        p.setName(name);
        p.setSku(sku.nextProductSku(ctx.company()));
        return products.save(p);
    }

    /** Takes a piece out of stock with a reason (damaged, lost, melted, given to karigar for remaking ...). */
    @Transactional
    public Map<String, Object> remove(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireManage(ctx.me(), "STOCK");
        String reason = Num.text(n, "reason", 500);
        if (reason == null) throw Errors.bad("Enter why the piece is removed from stock.");
        JewelleryItem i = items.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, i.getCompany(), i.getBranch());
        if (!JewelleryItem.IN_STOCK.equals(i.getStatus())) throw Errors.bad("Only pieces in stock can be removed.");
        i.setStatus(JewelleryItem.REMOVED);
        record(ctx, i, "ADJUSTMENT_OUT", null, null, reason);
        audit.log(ctx, "STOCK_REMOVE", "JewelleryItem", i.getId(), Map.of("status", "IN_STOCK"), Map.of("status", "REMOVED", "reason", reason, "tagNo", i.getTagNo()));
        return Views.item(i, cost(ctx));
    }

    @Transactional
    public Map<String, Object> restore(TenantContext.Ctx ctx, Long id) {
        permissions.requireManage(ctx.me(), "STOCK");
        JewelleryItem i = items.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, i.getCompany(), i.getBranch());
        if (!JewelleryItem.REMOVED.equals(i.getStatus())) throw Errors.bad("Only removed pieces can be put back.");
        i.setStatus(JewelleryItem.IN_STOCK);
        record(ctx, i, "ADJUSTMENT_IN", null, null, "Put back into stock");
        audit.log(ctx, "STOCK_RESTORE", "JewelleryItem", i.getId(), null, Map.of("tagNo", i.getTagNo()));
        return Views.item(i, cost(ctx));
    }

    /** Moves pieces to another branch of the company in one step: {toBranchId, itemIds:[...], notes}. */
    @Transactional
    public Map<String, Object> transfer(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireManage(ctx.me(), "STOCK");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Branch to = branches.findById(Num.requireId(n, "toBranchId", "Destination branch")).orElseThrow(TenantContext::notFound);
        if (!to.getCompany().getId().equals(ctx.companyId())) throw TenantContext.notFound();
        if (to.getId().equals(ctx.branchId())) throw Errors.bad("Pick a different branch to send the pieces to.");
        JsonNode ids = n.get("itemIds");
        if (ids == null || !ids.isArray() || ids.isEmpty()) throw Errors.bad("Pick the pieces to transfer.");
        StockTransfer t = new StockTransfer();
        t.setCompany(ctx.company());
        t.setFromBranch(ctx.branch());
        t.setToBranch(to);
        t.setTransferDate(GoldRateService.today());
        t.setTransferNo(numbering.next(ctx.company(), "TRF", t.getTransferDate()));
        t.setNotes(Num.text(n, "notes", 1000));
        t.setCreatedBy(tenant.user(ctx.me()));
        transfers.save(t);
        BigDecimal weight = BigDecimal.ZERO;
        List<Long> sorted = new ArrayList<>();
        for (JsonNode x : ids) sorted.add(x.asLong());
        Collections.sort(sorted);
        for (Long id : new LinkedHashSet<>(sorted)) {
            JewelleryItem i = items.lock(id).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, i.getCompany());
            if (!JewelleryItem.IN_STOCK.equals(i.getStatus()) || !i.getBranch().getId().equals(ctx.branchId()))
                throw Errors.bad("Piece " + i.getTagNo() + " is not in stock in this branch.");
            StockTransferItem ti = new StockTransferItem();
            ti.setTransfer(t);
            ti.setJewelleryItem(i);
            transferItems.save(ti);
            record(ctx, i, "TRANSFER_OUT", "STOCK_TRANSFER", t.getId(), "Sent to " + to.getName() + " (" + t.getTransferNo() + ")");
            i.setBranch(to);
            record(ctx, i, "TRANSFER_IN", "STOCK_TRANSFER", t.getId(), "Received from " + ctx.branch().getName() + " (" + t.getTransferNo() + ")");
            weight = weight.add(i.getNetWeight());
        }
        audit.log(ctx, "STOCK_TRANSFER", "StockTransfer", t.getId(), null, Map.of("transferNo", t.getTransferNo(), "to", to.getName(), "pieces", sorted.size()));
        return Map.of("id", t.getId(), "transferNo", t.getTransferNo(), "pieces", sorted.size(), "netWeight", weight, "to", to.getName());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> transfers(TenantContext.Ctx ctx) {
        permissions.requirePage(ctx.me(), "STOCK");
        List<Map<String, Object>> out = new ArrayList<>();
        for (StockTransfer t : transfers.list(ctx.companyId(), ctx.admin() ? null : ctx.branchId())) {
            List<StockTransferItem> its = transferItems.findByTransferId(t.getId());
            Map<String, Object> m = Views.map();
            m.put("id", t.getId());
            m.put("transferNo", t.getTransferNo());
            m.put("date", t.getTransferDate());
            m.put("from", t.getFromBranch().getName());
            m.put("to", t.getToBranch().getName());
            m.put("pieces", its.size());
            m.put("tags", its.stream().map(x -> x.getJewelleryItem().getTagNo()).toList());
            m.put("netWeight", its.stream().map(x -> x.getJewelleryItem().getNetWeight()).reduce(BigDecimal.ZERO, BigDecimal::add));
            m.put("by", t.getCreatedBy() == null ? null : t.getCreatedBy().getUsername());
            m.put("notes", t.getNotes());
            out.add(m);
        }
        return out;
    }

    /** Item timeline: purchase -> stock -> edits -> transfer -> sale -> return, with who and when. */
    @Transactional(readOnly = true)
    public Map<String, Object> timeline(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "STOCK");
        JewelleryItem i = load(ctx, id);
        List<Map<String, Object>> events = new ArrayList<>();
        for (StockMovement m : movements.findByJewelleryItemIdOrderByCreatedAtAscIdAsc(id)) {
            Map<String, Object> e = Views.map();
            e.put("at", m.getCreatedAt());
            e.put("date", m.getMovementDate());
            e.put("type", m.getMovementType());
            e.put("branch", m.getBranch().getName());
            e.put("notes", m.getNotes());
            e.put("by", m.getCreatedBy() == null ? null : m.getCreatedBy().getUsername());
            e.put("referenceType", m.getReferenceType());
            e.put("referenceId", m.getReferenceId());
            events.add(e);
        }
        Map<String, Object> out = Views.map();
        out.put("item", Views.item(i, cost(ctx)));
        out.put("events", events);
        out.put("sales", saleItems.findByJewelleryItemIdOrderByIdDesc(id).stream().map(si -> Map.of("saleId", si.getSale().getId(),
                "invoiceNo", si.getSale().getInvoiceNo(), "date", si.getSale().getSaleDate(), "total", si.getTotal(), "status", si.getSale().getStatus())).toList());
        out.put("purchases", purchaseItems.findByJewelleryItemId(id).stream().map(pi -> Map.of("purchaseId", pi.getPurchase().getId(),
                "purchaseNo", pi.getPurchase().getPurchaseNo(), "amount", pi.getAmount())).toList());
        return out;
    }

    JewelleryItem load(TenantContext.Ctx ctx, Long id) {
        JewelleryItem i = items.findById(id).orElseThrow(TenantContext::notFound);
        tenant.checkCompany(ctx, i.getCompany());
        return i;
    }

    void record(TenantContext.Ctx ctx, JewelleryItem i, String type, String refType, Long refId, String notes) {
        StockMovement m = new StockMovement();
        m.setCompany(ctx.company());
        m.setBranch(i.getBranch());
        m.setJewelleryItem(i);
        m.setMovementType(type);
        m.setReferenceType(refType);
        m.setReferenceId(refId);
        m.setMovementDate(GoldRateService.today());
        m.setNotes(notes);
        m.setCreatedBy(tenant.user(ctx.me()));
        movements.save(m);
    }
}
