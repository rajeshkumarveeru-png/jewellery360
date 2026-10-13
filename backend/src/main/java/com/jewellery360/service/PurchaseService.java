package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Num;
import com.jewellery360.support.Views;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.jewellery360.support.Num.*;

/**
 * Purchases from suppliers. Each line is either a tagged piece that goes straight into stock (with its cost) or plain metal /
 * other goods (no tag). Payments to the supplier and returns reduce what the shop owes; the supplier ledger shows the balance.
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {
    private final PurchaseOrderRepository purchases;
    private final PurchaseItemRepository lines;
    private final PurchaseReturnRepository returns;
    private final PurchaseReturnItemRepository returnItems;
    private final SupplierRepository suppliers;
    private final PaymentRepository payments;
    private final JewelleryItemRepository items;
    private final StockService stock;
    private final PaymentService paymentService;
    private final NumberingService numbering;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    /**
     * {supplierId, supplierInvoiceNo, purchaseDate, notes, gst, payment:{mode, amount, reference},
     *  items:[{description, purity, quantity, grossWeight, netWeight, rate, makingCharge, amount, addToStock, stock:{...stock fields}}]}
     * Lines with addToStock create a tagged piece (cost price = line amount).
     */
    @Transactional
    public Map<String, Object> create(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requireManage(ctx.me(), "PURCHASES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Supplier supplier = suppliers.findById(Num.requireId(body, "supplierId", "Supplier")).orElseThrow(TenantContext::notFound);
        tenant.checkCompany(ctx, supplier.getCompany());
        JsonNode list = body.get("items");
        if (list == null || !list.isArray() || list.isEmpty()) throw Errors.bad("Add at least one line.");
        PurchaseOrder p = new PurchaseOrder();
        p.setCompany(ctx.company());
        p.setBranch(ctx.branch());
        p.setSupplier(supplier);
        p.setPurchaseDate(Optional.ofNullable(Num.date(body, "purchaseDate")).orElse(GoldRateService.today()));
        p.setPurchaseNo(numbering.next(ctx.company(), "PUR", p.getPurchaseDate()));
        p.setSupplierInvoiceNo(Num.text(body, "supplierInvoiceNo", 80));
        p.setNotes(Num.text(body, "notes", 2000));
        p.setCreatedBy(tenant.user(ctx.me()));
        purchases.save(p);
        BigDecimal subtotal = BigDecimal.ZERO;
        int pieces = 0;
        for (JsonNode n : list) {
            PurchaseItem pi = new PurchaseItem();
            pi.setPurchase(p);
            pi.setDescription(Optional.ofNullable(Num.text(n, "description", 500)).orElse("Purchase"));
            pi.setPurity(Num.text(n, "purity", 50));
            BigDecimal q = Num.dec(n, "quantity");
            pi.setQuantity(q.signum() > 0 ? q : BigDecimal.ONE);
            pi.setGrossWeight(r3(Num.nonNegative(Num.dec(n, "grossWeight"), "Gross weight")));
            pi.setNetWeight(r3(Num.nonNegative(Num.dec(n, "netWeight"), "Net weight")));
            pi.setRate(r3(Num.nonNegative(Num.dec(n, "rate"), "Rate")));
            pi.setMakingCharge(r2(Num.nonNegative(Num.dec(n, "makingCharge"), "Making")));
            BigDecimal amount = Num.decOrNull(n, "amount");
            pi.setAmount(r2(amount != null ? Num.nonNegative(amount, "Amount") : pi.getNetWeight().multiply(pi.getRate()).add(pi.getMakingCharge())));
            if (pi.getAmount().signum() <= 0) throw Errors.bad("Each purchase line needs an amount.");
            if (Num.bool(n, "addToStock", false)) {
                JsonNode s = n.get("stock");
                if (s == null || !s.isObject()) throw Errors.bad("Enter the piece details for lines added to stock.");
                ObjectNode sn = ((ObjectNode) s).deepCopy();
                if (!sn.has("costPrice")) sn.put("costPrice", pi.getAmount());
                if (!sn.has("grossWeight")) sn.put("grossWeight", pi.getGrossWeight());
                if (!sn.has("netWeight") && pi.getNetWeight().signum() > 0) sn.put("netWeight", pi.getNetWeight());
                pi.setJewelleryItem(stock.createForPurchase(ctx, sn, p));
                pieces++;
            }
            lines.save(pi);
            subtotal = subtotal.add(pi.getAmount());
        }
        p.setSubtotal(subtotal);
        p.setGst(r2(Num.nonNegative(Num.dec(body, "gst"), "GST")));
        p.setTotal(subtotal.add(p.getGst()));
        JsonNode pay = body.get("payment");
        if (pay != null && Num.dec(pay, "amount").signum() > 0) pay(ctx, p, pay);
        audit.log(ctx, "PURCHASE_CREATE", "PurchaseOrder", p.getId(), null, Map.of("purchaseNo", p.getPurchaseNo(), "supplier", supplier.getName(),
                "total", p.getTotal(), "pieces", pieces));
        return detail(ctx, p.getId());
    }

    /** Pays the supplier against a purchase: {mode, amount, reference}. */
    @Transactional
    public Map<String, Object> addPayment(TenantContext.Ctx ctx, Long id, JsonNode body) {
        permissions.requireManage(ctx.me(), "PURCHASES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        PurchaseOrder p = purchases.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, p.getCompany(), p.getBranch());
        pay(ctx, p, body);
        audit.log(ctx, "SUPPLIER_PAYMENT", "PurchaseOrder", p.getId(), null, Map.of("purchaseNo", p.getPurchaseNo(), "amount", Num.dec(body, "amount")));
        return detail(ctx, id);
    }

    private void pay(TenantContext.Ctx ctx, PurchaseOrder p, JsonNode body) {
        BigDecimal amount = r2(Num.dec(body, "amount"));
        if (amount.compareTo(p.balance()) > 0) throw Errors.bad("That is more than the ₹" + r2(p.balance()).toPlainString() + " still owed on " + p.getPurchaseNo() + ".");
        paymentService.record(ctx, Payment.PAYOUT, PaymentService.moneyMode(Num.text(body, "mode")), amount,
                x -> { x.setSupplier(p.getSupplier()); x.setPurchase(p); }, Num.text(body, "reference", 160), "Payment for " + p.getPurchaseNo());
        p.setPaidAmount(p.getPaidAmount().add(amount));
    }

    /** Sends lines back to the supplier: {purchaseItemIds:[...], reason}. Tagged pieces leave stock. */
    @Transactional
    public Map<String, Object> returnItems(TenantContext.Ctx ctx, Long id, JsonNode body) {
        permissions.requireManage(ctx.me(), "PURCHASES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        PurchaseOrder p = purchases.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, p.getCompany(), p.getBranch());
        JsonNode ids = body.get("purchaseItemIds");
        if (ids == null || !ids.isArray() || ids.isEmpty()) throw Errors.bad("Pick the lines being returned.");
        PurchaseReturn r = new PurchaseReturn();
        r.setCompany(ctx.company());
        r.setBranch(ctx.branch());
        r.setPurchase(p);
        r.setReturnDate(GoldRateService.today());
        r.setReturnNo(numbering.next(ctx.company(), "PRET", r.getReturnDate()));
        r.setReason(Num.text(body, "reason", 1000));
        r.setCreatedBy(tenant.user(ctx.me()));
        returns.save(r);
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode n : ids) {
            PurchaseItem pi = lines.findById(n.asLong()).orElseThrow(TenantContext::notFound);
            if (!pi.getPurchase().getId().equals(p.getId())) throw Errors.bad("That line is not on this purchase.");
            if (returnItems.existsByPurchaseItemId(pi.getId())) throw Errors.bad("Line \"" + pi.getDescription() + "\" was already returned.");
            JewelleryItem item = pi.getJewelleryItem();
            if (item != null) {
                item = items.lock(item.getId()).orElseThrow();
                if (!JewelleryItem.IN_STOCK.equals(item.getStatus())) throw Errors.bad("Piece " + item.getTagNo() + " is no longer in stock and cannot be returned to the supplier.");
                item.setStatus(JewelleryItem.RETURNED_TO_SUPPLIER);
                stock.record(ctx, item, "PURCHASE_RETURN_OUT", "PURCHASE_RETURN", r.getId(), "Returned to " + p.getSupplier().getName() + " (" + r.getReturnNo() + ")");
            }
            PurchaseReturnItem ri = new PurchaseReturnItem();
            ri.setReturnRecord(r);
            ri.setPurchaseItem(pi);
            ri.setJewelleryItem(item);
            ri.setAmount(pi.getAmount());
            ri.setReason(r.getReason());
            returnItems.save(ri);
            total = total.add(pi.getAmount());
        }
        // GST on the returned part goes back too
        BigDecimal gstShare = p.getSubtotal().signum() == 0 ? BigDecimal.ZERO : r2(p.getGst().multiply(total).divide(p.getSubtotal(), 6, java.math.RoundingMode.HALF_UP));
        r.setAmount(total.add(gstShare));
        p.setReturnedAmount(p.getReturnedAmount().add(r.getAmount()));
        audit.log(ctx, "PURCHASE_RETURN", "PurchaseReturn", r.getId(), null, Map.of("purchaseNo", p.getPurchaseNo(), "amount", r.getAmount()));
        return detail(ctx, id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(TenantContext.Ctx ctx, LocalDate from, LocalDate to) {
        permissions.requirePage(ctx.me(), "PURCHASES");
        LocalDate end = to == null ? GoldRateService.today() : to;
        LocalDate start = from == null ? end.minusDays(90) : from;
        return purchases.between(ctx.companyId(), ctx.branchId(), start, end).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "PURCHASES");
        PurchaseOrder p = purchases.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, p.getCompany(), p.getBranch());
        Map<String, Object> m = summary(p);
        m.put("notes", p.getNotes());
        List<Map<String, Object>> ls = new ArrayList<>();
        for (PurchaseItem pi : lines.findByPurchaseIdOrderById(id)) {
            Map<String, Object> x = Views.map();
            x.put("id", pi.getId());
            x.put("description", pi.getDescription());
            x.put("purity", pi.getPurity());
            x.put("quantity", pi.getQuantity());
            x.put("grossWeight", pi.getGrossWeight());
            x.put("netWeight", pi.getNetWeight());
            x.put("rate", pi.getRate());
            x.put("makingCharge", pi.getMakingCharge());
            x.put("amount", pi.getAmount());
            x.put("itemId", pi.getJewelleryItem() == null ? null : pi.getJewelleryItem().getId());
            x.put("tagNo", pi.getJewelleryItem() == null ? null : pi.getJewelleryItem().getTagNo());
            x.put("returned", returnItems.existsByPurchaseItemId(pi.getId()));
            ls.add(x);
        }
        m.put("lines", ls);
        m.put("payments", payments.findByPurchaseIdOrderById(id).stream().map(Views::payment).toList());
        m.put("returns", returns.findByPurchaseIdOrderById(id).stream().map(r -> Map.of("id", r.getId(), "returnNo", r.getReturnNo(),
                "date", r.getReturnDate(), "amount", r.getAmount(), "reason", Objects.toString(r.getReason(), ""))).toList());
        return m;
    }

    /** Supplier ledger: purchases (shop owes), payments and returns (reduce it), running balance. */
    @Transactional(readOnly = true)
    public Map<String, Object> supplierLedger(TenantContext.Ctx ctx, Long supplierId) {
        permissions.requirePage(ctx.me(), "PURCHASES");
        Supplier s = suppliers.findById(supplierId).orElseThrow(TenantContext::notFound);
        tenant.checkCompany(ctx, s.getCompany());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PurchaseOrder p : purchases.findBySupplierIdOrderByPurchaseDateAscIdAsc(supplierId))
            rows.add(entry(p.getPurchaseDate(), "Purchase", p.getPurchaseNo(), p.getTotal(), BigDecimal.ZERO));
        for (PurchaseReturn r : returns.findByPurchaseSupplierIdOrderByReturnDateAsc(supplierId))
            rows.add(entry(r.getReturnDate(), "Return", r.getReturnNo(), BigDecimal.ZERO, r.getAmount()));
        for (Payment p : payments.findBySupplierIdOrderByPaymentDateAscIdAsc(supplierId))
            rows.add(entry(p.getPaymentDate(), "Payment (" + p.getPaymentMode() + ")", p.getPaymentNo(), BigDecimal.ZERO, p.getAmount()));
        rows.sort(Comparator.comparing(e -> (LocalDate) e.get("date")));
        BigDecimal bal = BigDecimal.ZERO;
        for (Map<String, Object> e : rows) {
            bal = bal.add((BigDecimal) e.get("credit")).subtract((BigDecimal) e.get("debit"));
            e.put("balance", bal);
        }
        Map<String, Object> m = Views.map();
        m.put("supplier", Map.of("id", s.getId(), "name", s.getName(), "phone", Objects.toString(s.getPhone(), "")));
        m.put("owed", bal);
        m.put("message", bal.signum() > 0 ? "You owe ₹" + r2(bal).toPlainString() : bal.signum() < 0 ? "Supplier owes you ₹" + r2(bal.negate()).toPlainString() : "Settled");
        m.put("ledger", rows);
        return m;
    }

    private Map<String, Object> entry(LocalDate d, String type, String ref, BigDecimal credit, BigDecimal debit) {
        Map<String, Object> m = Views.map();
        m.put("date", d);
        m.put("type", type);
        m.put("reference", ref);
        m.put("credit", credit);
        m.put("debit", debit);
        return m;
    }

    private Map<String, Object> summary(PurchaseOrder p) {
        Map<String, Object> m = Views.map();
        m.put("id", p.getId());
        m.put("purchaseNo", p.getPurchaseNo());
        m.put("supplierInvoiceNo", p.getSupplierInvoiceNo());
        m.put("date", p.getPurchaseDate());
        m.put("supplierId", p.getSupplier().getId());
        m.put("supplier", p.getSupplier().getName());
        m.put("branchName", p.getBranch().getName());
        m.put("subtotal", p.getSubtotal());
        m.put("gst", p.getGst());
        m.put("total", p.getTotal());
        m.put("paid", p.getPaidAmount());
        m.put("returned", p.getReturnedAmount());
        m.put("balance", p.balance());
        m.put("status", p.balance().signum() <= 0 ? "Paid" : p.getPaidAmount().signum() > 0 ? "Partially paid" : "Unpaid");
        return m;
    }
}
