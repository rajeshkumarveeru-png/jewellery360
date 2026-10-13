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
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.jewellery360.support.Num.*;

/**
 * The single billing workflow: Search / scan -> add -> review -> pay -> complete.
 *
 * Request body (preview, draft and complete share it):
 * <pre>
 * { clientRef, customerId | customer:{name, phone} | (none = walk-in), saleDate, customOrderId, notes, discount,
 *   items: [{itemId, rate?, wastagePercent?, makingType?, makingValue?, stoneCharge?, otherCharge?}],
 *   oldGold: [{description, purityId | purity, grossWeight, stoneWeight, netWeight?, rate?, deductionPercent}],
 *   oldGoldExcess: "ADVANCE" | "CASH" | "UPI" ...,
 *   payments: [{mode: CASH|UPI|CARD|BANK|CHEQUE|ADVANCE|OLD_GOLD, amount, reference, oldGoldId?}] }
 * </pre>
 * Whatever is not paid stays as credit (the customer owes it). Walk-in bills must be paid in full.
 */
@Service
@RequiredArgsConstructor
public class SalesService {
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SaleReturnRepository saleReturns;
    private final SaleReturnItemRepository saleReturnItems;
    private final JewelleryItemRepository items;
    private final StockMovementRepository movements;
    private final PaymentRepository payments;
    private final OldGoldTransactionRepository oldGoldRepo;
    private final CustomerAdvanceRepository advances;
    private final CustomOrderRepository orders;
    private final CustomerService customers;
    private final OldGoldService oldGoldService;
    private final PaymentService paymentService;
    private final PricingService pricing;
    private final GoldRateService goldRates;
    private final CompanyPropertyService settings;
    private final NumberingService numbering;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    /* =================================================================== preview / create ==================== */

    /** Prices the bill exactly as completing it would, without saving anything. */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requirePage(ctx.me(), "BILLING");
        Prepared p = prepare(ctx, body, false);
        return previewView(p);
    }

    @Transactional
    public Map<String, Object> create(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requireWrite(ctx.me(), "BILLING");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        String clientRef = Num.text(body, "clientRef", 64);
        if (clientRef != null) {
            Optional<Sale> existing = sales.findByCompanyIdAndClientRef(ctx.companyId(), clientRef);
            if (existing.isPresent()) return detail(ctx, existing.get().getId());   // a retried request returns the bill it already made
        }
        boolean draft = Num.bool(body, "draft", false);
        Long draftId = Num.id(body, "draftId");
        Sale sale;
        if (draftId != null) {
            sale = sales.lock(draftId).orElseThrow(TenantContext::notFound);
            tenant.check(ctx, sale.getCompany(), sale.getBranch());
            if (!Sale.DRAFT.equals(sale.getStatus())) throw Errors.bad("This bill is no longer a draft.");
            saleItems.deleteAll(saleItems.findBySaleIdOrderById(sale.getId()));
            saleItems.flush();
        } else {
            sale = new Sale();
            sale.setCompany(ctx.company());
            sale.setBranch(ctx.branch());
            sale.setCreatedBy(tenant.user(ctx.me()));
        }
        Prepared p = prepare(ctx, body, !draft);
        sale.setCustomer(p.customer);
        sale.setClientRef(clientRef);
        sale.setSaleDate(p.saleDate);
        sale.setNotes(Num.text(body, "notes", 1000));
        sale.setCustomOrder(p.order);
        copyTotals(sale, p);
        if (draft) {
            sale.setStatus(Sale.DRAFT);
            if (sale.getInvoiceNo() == null) sale.setInvoiceNo("DRAFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
            sales.save(sale);
            saveLines(sale, p);
            audit.log(ctx, "BILL_DRAFT_SAVE", "Sale", sale.getId(), null, Map.of("items", p.lines.size(), "total", sale.getTotal()));
            return detail(ctx, sale.getId());
        }

        sale.setStatus(Sale.COMPLETED);
        String typed = Num.bool(body, "invoiceNoAuto", true) ? null : Num.text(body, "invoiceNo");
        if (typed != null) {
            String problem = invoiceNoProblem(ctx, typed);
            if (problem != null) throw Errors.bad(problem);
            sale.setInvoiceNo(typed);
        } else {
            sale.setInvoiceNo(numbering.next(ctx.company(), "INV", sale.getSaleDate(), no -> invoiceNoUsed(ctx, no)));
        }
        sale.setCreatedAt(Instant.now());
        sales.save(sale);
        saveLines(sale, p);
        for (Line l : p.lines) {
            JewelleryItem item = l.item;
            item.setStatus(JewelleryItem.SOLD);
            item.setSoldAt(Instant.now());
            movement(ctx, item, "SALE_OUT", "SALE", sale.getId(), "Sold on " + sale.getInvoiceNo());
        }

        // Old gold exchanged on this bill: recorded, valued, and adjusted first.
        if (!p.oldGold.isEmpty()) {
            OldGoldTransaction og = oldGoldService.save(ctx, p.customer, "EXCHANGE", p.oldGold, "Exchanged on " + sale.getInvoiceNo());
            BigDecimal use = og.getTotalAmount().min(sale.getTotal());
            if (use.signum() > 0) {
                og.setUsedAmount(use);
                paymentService.record(ctx, Payment.RECEIPT, Payment.MODE_OLD_GOLD, use, x -> { x.setCustomer(p.customer); x.setSale(sale); x.setOldGold(og); },
                        og.getTransactionNo(), "Old gold exchange");
                paymentService.applyToSale(sale, use);
            }
            BigDecimal excess = og.getTotalAmount().subtract(use);
            if (excess.signum() > 0) settleOldGoldExcess(ctx, p.customer, og, excess, Num.text(body, "oldGoldExcess"));
            og.setStatus(og.available().signum() > 0 ? "OPEN" : "SETTLED");
        }
        applyPayments(ctx, sale, p.order, body.get("payments"));
        if (CustomerService.isWalkIn(p.customer) && sale.balance().signum() > 0)
            throw Errors.bad("A walk-in bill must be paid in full. Add the customer's name and phone to give credit.");
        if (p.order != null) p.order.setStatus("DELIVERED");
        audit.log(ctx, "BILL_COMPLETE", "Sale", sale.getId(), null, Map.of("invoiceNo", sale.getInvoiceNo(), "total", sale.getTotal(),
                "discount", sale.getDiscount(), "paid", sale.getPaidAmount(), "items", p.lines.size()));
        if (p.overridden) audit.log(ctx, "BILL_PRICE_OVERRIDE", "Sale", sale.getId(), null, Map.of("invoiceNo", sale.getInvoiceNo(), "lines", p.overrideNotes));
        return detail(ctx, sale.getId());
    }

    /* =================================================================== invoice numbers ===================== */

    /** GST rule 46(b): up to 16 characters, letters / digits / hyphen / slash, unique in the company. */
    private static final java.util.regex.Pattern INVOICE_NO = java.util.regex.Pattern.compile("[A-Za-z0-9/-]{1,16}");

    /** The number the next bill dated {@code date} will get if the counter keeps the suggestion. */
    @Transactional(readOnly = true)
    public Map<String, Object> nextInvoiceNo(TenantContext.Ctx ctx, LocalDate date) {
        permissions.requirePage(ctx.me(), "BILLING");
        LocalDate d = date == null ? GoldRateService.today() : date;
        return Map.of("invoiceNo", numbering.peek(ctx.company(), "INV", d, no -> invoiceNoUsed(ctx, no)), "saleDate", d);
    }

    /** Live check for a typed invoice number: {ok, message}. */
    @Transactional(readOnly = true)
    public Map<String, Object> checkInvoiceNo(TenantContext.Ctx ctx, String invoiceNo) {
        permissions.requirePage(ctx.me(), "BILLING");
        String problem = invoiceNoProblem(ctx, invoiceNo == null ? "" : invoiceNo.trim());
        return problem == null ? Map.of("ok", true) : Map.of("ok", false, "message", problem);
    }

    private String invoiceNoProblem(TenantContext.Ctx ctx, String no) {
        if (no.isEmpty()) return "Enter an invoice number, or keep the suggested one.";
        if (no.length() > 16) return "Invoice number can be at most 16 characters (GST rule).";
        if (!INVOICE_NO.matcher(no).matches()) return "Invoice number can only use letters, digits, '-' and '/'.";
        if (no.toUpperCase(Locale.ROOT).startsWith("DRAFT")) return "Invoice number cannot start with DRAFT.";
        if (invoiceNoUsed(ctx, no)) return "Invoice number " + no + " is already used.";
        return null;
    }

    private boolean invoiceNoUsed(TenantContext.Ctx ctx, String no) {
        return sales.existsByCompanyIdAndInvoiceNoIgnoreCase(ctx.companyId(), no);
    }

    @Transactional
    public void deleteDraft(TenantContext.Ctx ctx, Long id) {
        permissions.requireWrite(ctx.me(), "BILLING");
        Sale s = sales.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, s.getCompany(), s.getBranch());
        if (!Sale.DRAFT.equals(s.getStatus())) throw Errors.bad("Only drafts can be deleted. Cancel a completed bill instead.");
        saleItems.deleteAll(saleItems.findBySaleIdOrderById(id));
        sales.delete(s);
        audit.log(ctx, "BILL_DRAFT_DELETE", "Sale", id, null, null);
    }

    /* =================================================================== payments ============================ */

    /** Takes (more) money against a completed bill: {payments:[{mode, amount, reference}]}. */
    @Transactional
    public Map<String, Object> addPayments(TenantContext.Ctx ctx, Long id, JsonNode body) {
        permissions.requireWrite(ctx.me(), "BILLING");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Sale s = sales.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, s.getCompany(), s.getBranch());
        if (!Sale.COMPLETED.equals(s.getStatus()) && !Sale.PARTIALLY_RETURNED.equals(s.getStatus())) throw Errors.bad("Payments can only be added to a completed bill.");
        applyPayments(ctx, s, s.getCustomOrder(), body.get("payments"));
        audit.log(ctx, "BILL_PAYMENT", "Sale", s.getId(), null, Map.of("invoiceNo", s.getInvoiceNo(), "paid", s.getPaidAmount()));
        return detail(ctx, id);
    }

    private void applyPayments(TenantContext.Ctx ctx, Sale sale, CustomOrder order, JsonNode list) {
        if (list == null || !list.isArray()) return;
        BigDecimal requested = BigDecimal.ZERO;
        for (JsonNode n : list) requested = requested.add(Num.dec(n, "amount"));
        if (requested.compareTo(sale.balance()) > 0)
            throw Errors.bad("Payments (₹" + r2(requested).toPlainString() + ") are more than the amount due (₹" + r2(sale.balance()).toPlainString() + "). Give the change back instead.");
        Customer customer = sale.getCustomer();
        for (JsonNode n : list) {
            BigDecimal amount = r2(Num.dec(n, "amount"));
            if (amount.signum() == 0) continue;
            if (amount.signum() < 0) throw Errors.bad("Payment amounts cannot be negative.");
            String mode = Optional.ofNullable(Num.text(n, "mode")).orElse("CASH").toUpperCase(Locale.ROOT);
            String ref = Num.text(n, "reference", 160);
            if (Payment.MODE_ADVANCE.equals(mode)) {
                paymentService.useAdvances(ctx, customer, amount, order, x -> x.setSale(sale));
            } else if (Payment.MODE_OLD_GOLD.equals(mode)) {
                Long ogId = Num.requireId(n, "oldGoldId", "Old gold receipt");
                OldGoldTransaction og = oldGoldRepo.lock(ogId).orElseThrow(TenantContext::notFound);
                tenant.checkCompany(ctx, og.getCompany());
                if (!og.getCustomer().getId().equals(customer.getId())) throw Errors.bad("That old gold belongs to another customer.");
                if (amount.compareTo(og.available()) > 0) throw Errors.bad("Only ₹" + r2(og.available()).toPlainString() + " of that old gold value is left.");
                og.setUsedAmount(og.getUsedAmount().add(amount));
                og.setStatus(og.available().signum() > 0 ? "OPEN" : "SETTLED");
                paymentService.record(ctx, Payment.RECEIPT, Payment.MODE_OLD_GOLD, amount, x -> { x.setCustomer(customer); x.setSale(sale); x.setOldGold(og); }, og.getTransactionNo(), "Old gold exchange");
            } else {
                paymentService.record(ctx, Payment.RECEIPT, PaymentService.moneyMode(mode), amount, x -> { x.setCustomer(customer); x.setSale(sale); }, ref, null);
            }
            paymentService.applyToSale(sale, amount);
        }
    }

    /* =================================================================== cancel / return ===================== */

    /**
     * Cancels a completed bill: pieces go back to stock, every payment is reversed - advance and old-gold adjustments
     * are given back to the customer, money received is refunded (refundMode CASH/UPI/... or ADVANCE to keep it as credit).
     */
    @Transactional
    public Map<String, Object> cancel(TenantContext.Ctx ctx, Long id, JsonNode body) {
        permissions.requireWrite(ctx.me(), "BILLING");
        permissions.requirePower(ctx.me(), "CANCEL_RETURN");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        String reason = Num.text(body, "reason", 500);
        if (reason == null) throw Errors.bad("Enter the reason for cancelling.");
        Sale s = sales.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, s.getCompany(), s.getBranch());
        if (!Sale.COMPLETED.equals(s.getStatus())) throw Errors.bad(Sale.DRAFT.equals(s.getStatus()) ? "Delete the draft instead."
                : "Only a completed bill without returns can be cancelled (this bill is " + Views.billLabel(s).toLowerCase(Locale.ROOT) + ").");
        Map<String, Object> before = Views.saleSummary(s);
        for (SaleItem si : saleItems.findBySaleIdOrderById(id)) {
            JewelleryItem item = items.lock(si.getJewelleryItem().getId()).orElseThrow();
            if (!JewelleryItem.SOLD.equals(item.getStatus())) throw Errors.bad("Piece " + item.getTagNo() + " is no longer marked as sold; it cannot be put back.");
            item.setStatus(JewelleryItem.IN_STOCK);
            item.setSoldAt(null);
            item.setBranch(s.getBranch());
            movement(ctx, item, "SALE_CANCEL_IN", "SALE", s.getId(), "Bill " + s.getInvoiceNo() + " cancelled");
        }
        BigDecimal moneyToRefund = reversePayments(ctx, s);
        if (moneyToRefund.signum() > 0) paymentService.refundOnSale(ctx, s, moneyToRefund, Num.text(body, "refundMode"), "Bill " + s.getInvoiceNo() + " cancelled");
        s.setStatus(Sale.CANCELLED);
        s.setCancelReason(reason);
        s.setCancelledAt(Instant.now());
        if (s.getCustomOrder() != null && "DELIVERED".equals(s.getCustomOrder().getStatus())) s.getCustomOrder().setStatus("READY");
        audit.log(ctx, "BILL_CANCEL", "Sale", s.getId(), before, Map.of("status", "CANCELLED", "reason", reason, "refunded", moneyToRefund));
        return detail(ctx, id);
    }

    /** Gives back advance and old-gold adjustments; returns the money (cash/UPI/...) that still has to be refunded. */
    private BigDecimal reversePayments(TenantContext.Ctx ctx, Sale s) {
        BigDecimal money = BigDecimal.ZERO;
        for (Payment p : payments.findBySaleIdOrderById(s.getId())) {
            int sign = Payment.REFUND.equals(p.getPaymentType()) ? -1 : 1;
            if (Payment.MODE_ADVANCE.equals(p.getPaymentMode()) && sign > 0 && p.getAdvance() != null) {
                CustomerAdvance a = p.getAdvance();
                a.setUsedAmount(a.getUsedAmount().subtract(p.getAmount()));
                a.recalc();
                paymentService.record(ctx, Payment.REFUND, Payment.MODE_ADVANCE, p.getAmount(), x -> { x.setCustomer(s.getCustomer()); x.setSale(s); x.setAdvance(a); },
                        a.getAdvanceNo(), "Advance given back - bill cancelled");
                paymentService.applyToSale(s, p.getAmount().negate());
            } else if (Payment.MODE_OLD_GOLD.equals(p.getPaymentMode()) && sign > 0 && p.getOldGold() != null) {
                OldGoldTransaction og = p.getOldGold();
                og.setUsedAmount(og.getUsedAmount().subtract(p.getAmount()));
                og.setStatus("OPEN");
                paymentService.record(ctx, Payment.REFUND, Payment.MODE_OLD_GOLD, p.getAmount(), x -> { x.setCustomer(s.getCustomer()); x.setSale(s); x.setOldGold(og); },
                        og.getTransactionNo(), "Old gold value given back - bill cancelled");
                paymentService.applyToSale(s, p.getAmount().negate());
            } else if (p.isMoney() || Payment.MODE_ADVANCE.equals(p.getPaymentMode())) {
                money = money.add(p.getAmount().multiply(BigDecimal.valueOf(sign)));
            }
        }
        return money.max(BigDecimal.ZERO);
    }

    /**
     * Returns pieces from a bill: {saleItemIds:[...], reason, refundMode}. The pieces go back to stock; if the customer has
     * now paid more than the reduced bill, the difference is refunded (or kept as advance with refundMode ADVANCE).
     */
    @Transactional
    public Map<String, Object> returnItems(TenantContext.Ctx ctx, Long id, JsonNode body) {
        permissions.requireWrite(ctx.me(), "BILLING");
        permissions.requirePower(ctx.me(), "CANCEL_RETURN");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Sale s = sales.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, s.getCompany(), s.getBranch());
        if (!Sale.COMPLETED.equals(s.getStatus()) && !Sale.PARTIALLY_RETURNED.equals(s.getStatus())) throw Errors.bad("Only completed bills can take returns.");
        JsonNode ids = body.get("saleItemIds");
        if (ids == null || !ids.isArray() || ids.isEmpty()) throw Errors.bad("Pick the pieces being returned.");
        String reason = Num.text(body, "reason", 1000);
        SaleReturn r = new SaleReturn();
        r.setCompany(ctx.company());
        r.setBranch(ctx.branch());
        r.setSale(s);
        r.setReturnDate(GoldRateService.today());
        r.setReturnNo(numbering.next(ctx.company(), "RET", r.getReturnDate()));
        r.setReason(reason);
        r.setCreatedBy(tenant.user(ctx.me()));
        saleReturns.save(r);
        BigDecimal amount = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (JsonNode n : ids) {
            Long sid = n.asLong();
            if (!seen.add(sid)) continue;
            SaleItem si = saleItems.findById(sid).orElseThrow(TenantContext::notFound);
            if (!si.getSale().getId().equals(s.getId())) throw Errors.bad("That piece is not on this bill.");
            if (saleReturnItems.existsBySaleItemId(sid)) throw Errors.bad("Piece " + si.getTagNo() + " was already returned.");
            JewelleryItem item = items.lock(si.getJewelleryItem().getId()).orElseThrow();
            SaleReturnItem ri = new SaleReturnItem();
            ri.setReturnRecord(r);
            ri.setSaleItem(si);
            ri.setJewelleryItem(item);
            ri.setAmount(si.getTotal());
            ri.setReason(reason);
            saleReturnItems.save(ri);
            amount = amount.add(si.getTotal());
            item.setStatus(JewelleryItem.IN_STOCK);
            item.setSoldAt(null);
            item.setBranch(ctx.branch());
            movement(ctx, item, "SALE_RETURN_IN", "SALE_RETURN", r.getId(), "Returned on " + r.getReturnNo() + " (bill " + s.getInvoiceNo() + ")");
        }
        // the rounding off of the bill belongs to the whole bill: give it back with the last pieces
        long lines = saleItems.findBySaleIdOrderById(s.getId()).size();
        long returned = saleReturnItems.findByReturnRecordSaleId(s.getId()).size();
        if (returned == lines) amount = s.getTotal().subtract(s.getReturnedAmount());
        r.setAmount(r2(amount));
        s.setReturnedAmount(s.getReturnedAmount().add(r.getAmount()));
        s.setStatus(returned == lines ? Sale.RETURNED : Sale.PARTIALLY_RETURNED);
        BigDecimal refundable = s.getPaidAmount().subtract(s.getTotal().subtract(s.getReturnedAmount()));
        if (refundable.signum() > 0) paymentService.refundOnSale(ctx, s, refundable, Num.text(body, "refundMode"), "Return " + r.getReturnNo());
        else paymentService.applyToSale(s, BigDecimal.ZERO);
        audit.log(ctx, "BILL_RETURN", "SaleReturn", r.getId(), null, Map.of("invoiceNo", s.getInvoiceNo(), "returnNo", r.getReturnNo(), "amount", r.getAmount(),
                "refunded", refundable.max(BigDecimal.ZERO)));
        Map<String, Object> out = detail(ctx, id);
        out.put("returnId", r.getId());
        out.put("returnNo", r.getReturnNo());
        out.put("returnAmount", r.getAmount());
        out.put("refunded", refundable.max(BigDecimal.ZERO));
        return out;
    }

    /* =================================================================== read ================================ */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(TenantContext.Ctx ctx, LocalDate from, LocalDate to, String status, String q, int limit) {
        permissions.requirePage(ctx.me(), "BILLING");
        LocalDate end = to == null ? GoldRateService.today() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        String st = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(Locale.ROOT);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        List<Sale> rows;
        if ("UNPAID".equals(st)) rows = sales.unpaid(ctx.companyId(), ctx.branchId());
        else rows = sales.list(ctx.companyId(), ctx.branchId(), start, end, st, like, PageRequest.of(0, Math.max(1, Math.min(limit, 1000))));
        return rows.stream().map(Views::saleSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(TenantContext.Ctx ctx, Long id) {
        // a bill is opened from Billing or from a customer's history
        if (!permissions.can(ctx.me(), "CUSTOMERS")) permissions.requirePage(ctx.me(), "BILLING");
        Sale s = sales.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, s.getCompany(), s.getBranch());
        Map<String, Object> m = Views.saleSummary(s);
        m.put("customer", Views.customer(s.getCustomer()));
        m.put("walkIn", CustomerService.isWalkIn(s.getCustomer()));
        m.put("notes", s.getNotes());
        m.put("cancelReason", s.getCancelReason());
        m.put("customOrderNo", s.getCustomOrder() == null ? null : s.getCustomOrder().getOrderNo());
        Map<String, Object> totals = Views.map();
        totals.put("metalValue", s.getMetalValue());
        totals.put("wastage", s.getWastageValue());
        totals.put("making", s.getMakingValue());
        totals.put("stone", s.getStoneValue());
        totals.put("other", s.getOtherValue());
        totals.put("subtotal", s.getSubtotal());
        totals.put("discount", s.getDiscount());
        totals.put("taxable", s.getTaxableValue());
        totals.put("taxMode", s.getTaxMode());
        totals.put("taxRate", s.getTaxRate());
        totals.put("cgstRate", s.getCgstRate());
        totals.put("sgstRate", s.getSgstRate());
        totals.put("gst", s.getGst());
        totals.put("roundOff", s.getRoundOff());
        totals.put("total", s.getTotal());
        m.put("totals", totals);
        Set<Long> returnedLines = new HashSet<>();
        for (SaleReturnItem ri : saleReturnItems.findByReturnRecordSaleId(id)) returnedLines.add(ri.getSaleItem().getId());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (SaleItem si : saleItems.findBySaleIdOrderById(id)) {
            Map<String, Object> l = lineView(si);
            l.put("returned", returnedLines.contains(si.getId()));
            lines.add(l);
        }
        m.put("lines", lines);
        m.put("payments", payments.findBySaleIdOrderById(id).stream().map(Views::payment).toList());
        m.put("returns", saleReturns.findBySaleIdOrderById(id).stream().map(r -> Map.of("id", r.getId(), "returnNo", r.getReturnNo(),
                "date", r.getReturnDate(), "amount", r.getAmount(), "reason", Objects.toString(r.getReason(), ""))).toList());
        return m;
    }

    public static Map<String, Object> lineView(SaleItem si) {
        Map<String, Object> l = Views.map();
        l.put("id", si.getId());
        l.put("itemId", si.getJewelleryItem().getId());
        l.put("tagNo", si.getTagNo());
        l.put("description", si.getDescription());
        l.put("hsnCode", si.getHsnCode());
        l.put("huid", si.getHuid());
        l.put("purity", si.getPurity());
        l.put("grossWeight", si.getGrossWeight());
        l.put("stoneWeight", si.getStoneWeight());
        l.put("netWeight", si.getNetWeight());
        l.put("rate", si.getRate());
        l.put("metalValue", si.getMetalValue());
        l.put("wastagePercent", si.getWastagePercent());
        l.put("wastageValue", si.getWastageValue());
        l.put("makingType", si.getMakingType());
        l.put("makingValue", si.getMakingValue());
        l.put("makingCharge", si.getMakingCharge());
        l.put("stoneCharge", si.getStoneCharge());
        l.put("otherCharge", si.getOtherCharge());
        l.put("discount", si.getDiscount());
        l.put("taxable", si.getTaxableValue());
        l.put("gst", si.getGst());
        l.put("total", si.getTotal());
        return l;
    }

    /* =================================================================== internals =========================== */

    private static final class Line {
        JewelleryItem item;
        BigDecimal rate, wastagePercent, makingValue, stoneCharge, otherCharge;
        String makingType;
        boolean rateMissing;
        PricingService.LineResult result;
    }

    private static final class Prepared {
        Customer customer;
        LocalDate saleDate;
        CustomOrder order;
        List<Line> lines = new ArrayList<>();
        List<OldGoldService.Line> oldGold = new ArrayList<>();
        PricingService.Tax tax;
        PricingService.BillResult bill;
        BigDecimal oldGoldValue = BigDecimal.ZERO;
        boolean overridden;
        List<String> overrideNotes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
    }

    /** Validates and prices a request. With forCompletion the pieces are row-locked and every rule is strict. */
    private Prepared prepare(TenantContext.Ctx ctx, JsonNode body, boolean forCompletion) {
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Prepared p = new Prepared();
        p.saleDate = Optional.ofNullable(Num.date(body, "saleDate")).orElse(GoldRateService.today());
        if (p.saleDate.isAfter(GoldRateService.today())) throw Errors.bad("The bill date cannot be in the future.");

        Long customerId = Num.id(body, "customerId");
        JsonNode newCustomer = body.get("customer");
        if (customerId != null) p.customer = customers.get(ctx, customerId);
        else if (newCustomer != null && newCustomer.isObject() && Num.text(newCustomer, "name") != null) {
            if (!forCompletion) {
                Customer preview = new Customer();
                preview.setName(Num.text(newCustomer, "name"));
                preview.setPhone(Num.text(newCustomer, "phone"));
                p.customer = preview;
            } else {
                Map<String, Object> created = customers.create(ctx, newCustomer);
                p.customer = customers.get(ctx, (Long) created.get("id"));
            }
        } else p.customer = forCompletion ? customers.walkIn(ctx) : walkInPreview();
        if (p.customer.getId() != null && !p.customer.isActive()) throw Errors.bad("This customer has been removed.");

        Long orderId = Num.id(body, "customOrderId");
        if (orderId != null) {
            p.order = orders.findById(orderId).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, p.order.getCompany());
            if (List.of("DELIVERED", "CANCELLED").contains(p.order.getStatus())) throw Errors.bad("Order " + p.order.getOrderNo() + " is already " + p.order.getStatus().toLowerCase(Locale.ROOT) + ".");
            if (p.customer.getId() != null && !p.order.getCustomer().getId().equals(p.customer.getId())) throw Errors.bad("That order belongs to another customer.");
        }

        JsonNode list = body.get("items");
        if (list == null || !list.isArray() || list.isEmpty()) throw Errors.bad("Add at least one piece to the bill.");
        Map<Long, GoldRate> rates = goldRates.rateMap(ctx.branchId(), GoldRateService.today());
        boolean canOverride = permissions.has(ctx.me(), "PRICE_OVERRIDE");
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : list) ids.add(Num.requireId(n, "itemId", "Piece"));
        if (new HashSet<>(ids).size() != ids.size()) throw Errors.bad("The same piece is on the bill twice.");
        List<Long> lockOrder = new ArrayList<>(ids);
        Collections.sort(lockOrder);                         // lock in id order so two counters can never deadlock
        Map<Long, JewelleryItem> loaded = new HashMap<>();
        for (Long id : lockOrder) loaded.put(id, (forCompletion ? items.lock(id) : items.findById(id)).orElseThrow(() -> Errors.bad("Piece not found.")));

        int i = 0;
        for (JsonNode n : list) {
            JewelleryItem item = loaded.get(ids.get(i++));
            tenant.checkCompany(ctx, item.getCompany());
            if (!JewelleryItem.IN_STOCK.equals(item.getStatus())) throw Errors.bad("Piece " + item.getTagNo() + " is not in stock (" + item.getStatus().replace('_', ' ').toLowerCase(Locale.ROOT) + ").");
            if (!item.getBranch().getId().equals(ctx.branchId())) throw Errors.bad("Piece " + item.getTagNo() + " is in branch " + item.getBranch().getName() + ". Transfer it first.");
            Line l = new Line();
            l.item = item;
            BigDecimal defaultRate = BigDecimal.ZERO;
            if (!positive(item.getMrp())) {
                if (p.order != null && positive(p.order.getLockedRate()) && p.order.getPurity() != null && p.order.getPurity().getId().equals(item.getPurity().getId()))
                    defaultRate = p.order.getLockedRate();
                else {
                    GoldRate r = rates.get(item.getPurity().getId());
                    if (r != null) {
                        defaultRate = r.getRatePerGram();
                        if (!GoldRateService.today().equals(r.getRateDate())) p.warnings.add("Today's " + item.getPurity().getName() + " rate is not set; using the rate of " + r.getRateDate() + ".");
                    }
                }
            }
            l.rate = pick(n, "rate", defaultRate, canOverride, p, item, "rate");
            l.wastagePercent = pick(n, "wastagePercent", item.getWastagePercent(), canOverride, p, item, "wastage");
            l.makingValue = pick(n, "makingValue", item.getMakingValue(), canOverride, p, item, "making");
            l.stoneCharge = pick(n, "stoneCharge", item.getStoneValue(), canOverride, p, item, "stone");
            l.otherCharge = pick(n, "otherCharge", item.getOtherCharge(), canOverride, p, item, "other charge");
            String mt = Num.text(n, "makingType");
            l.makingType = mt == null ? item.getMakingType() : mt.toUpperCase(Locale.ROOT);
            if (!l.makingType.equals(item.getMakingType())) markOverride(canOverride, p, item, "making type");
            if (!positive(item.getMrp()) && l.rate.signum() <= 0) {
                l.rateMissing = true;
                if (forCompletion) throw Errors.bad("Set today's " + item.getPurity().getName() + " rate in Gold rates before billing " + item.getTagNo() + ".");
                p.warnings.add("No " + item.getPurity().getName() + " rate set - enter today's rate in Gold rates.");
            }
            l.result = pricing.line(new PricingService.LineInput(item.getNetWeight(), l.rate, l.wastagePercent, l.makingType, l.makingValue,
                    l.stoneCharge, l.otherCharge, item.getMrp()));
            p.lines.add(l);
        }

        BigDecimal discount = Num.dec(body, "discount");
        if (discount.signum() > 0) {
            if (!permissions.has(ctx.me(), "DISCOUNT")) throw Errors.forbidden("You do not have permission to give a discount.");
        }
        CompanyPropertyService.TaxSettings ts = settings.getTaxSettings(ctx.company());
        p.tax = new PricingService.Tax(ts.enabled(), ts.mode(), ts.rate(), ts.cgstRate(), ts.sgstRate());
        p.bill = pricing.bill(p.lines.stream().map(l -> l.result).toList(), discount, p.tax);

        p.oldGold = oldGoldService.parseAll(ctx, body.get("oldGold"));
        p.oldGoldValue = OldGoldService.total(p.oldGold);
        return p;
    }

    private Customer walkInPreview() {
        Customer c = new Customer();
        c.setName(CustomerService.WALK_IN);
        return c;
    }

    private BigDecimal pick(JsonNode n, String field, BigDecimal def, boolean canOverride, Prepared p, JewelleryItem item, String label) {
        BigDecimal v = Num.decOrNull(n, field);
        if (v == null) return nz(def);
        Num.nonNegative(v, label);
        if (v.compareTo(nz(def)) != 0) markOverride(canOverride, p, item, label);
        return v;
    }

    private void markOverride(boolean canOverride, Prepared p, JewelleryItem item, String what) {
        if (!canOverride) throw Errors.forbidden("You do not have permission to change the " + what + " on piece " + item.getTagNo() + ".");
        p.overridden = true;
        p.overrideNotes.add(item.getTagNo() + ": " + what);
    }

    /** Old gold worth more than the new bill: the difference is kept as advance (default) or paid out. */
    private void settleOldGoldExcess(TenantContext.Ctx ctx, Customer customer, OldGoldTransaction og, BigDecimal excess, String how) {
        String mode = how == null ? "ADVANCE" : how.toUpperCase(Locale.ROOT);
        if (CustomerService.isWalkIn(customer) && "ADVANCE".equals(mode)) mode = "CASH";
        og.setUsedAmount(og.getUsedAmount().add(excess));
        if ("ADVANCE".equals(mode)) {
            paymentService.createAdvance(ctx, customer, excess, null, "OLD_GOLD", null, og.getTransactionNo(), "Old gold value above the bill");
        } else {
            paymentService.record(ctx, Payment.PAYOUT, PaymentService.moneyMode(mode), excess, x -> { x.setCustomer(customer); x.setOldGold(og); }, og.getTransactionNo(),
                    "Old gold value above the bill paid to the customer");
        }
    }

    private void copyTotals(Sale s, Prepared p) {
        PricingService.BillResult b = p.bill;
        s.setMetalValue(b.metalValue());
        s.setWastageValue(b.wastageValue());
        s.setMakingValue(b.makingValue());
        s.setStoneValue(b.stoneValue());
        s.setOtherValue(b.otherValue());
        s.setSubtotal(b.subtotal());
        s.setDiscount(b.discount());
        s.setTaxableValue(b.taxable());
        s.setTaxMode(p.tax.effectiveMode());
        s.setTaxRate(p.tax.enabled() ? p.tax.totalRate() : BigDecimal.ZERO);
        s.setCgstRate(p.tax.enabled() && "CGST_SGST".equals(p.tax.mode()) ? p.tax.cgstRate() : BigDecimal.ZERO);
        s.setSgstRate(p.tax.enabled() && "CGST_SGST".equals(p.tax.mode()) ? p.tax.sgstRate() : BigDecimal.ZERO);
        s.setGst(b.gst());
        s.setRoundOff(b.roundOff());
        s.setTotal(b.total());
        s.setPaymentStatus("UNPAID");
    }

    private void saveLines(Sale sale, Prepared p) {
        for (int i = 0; i < p.lines.size(); i++) {
            Line l = p.lines.get(i);
            PricingService.LineResult r = p.bill.lines().get(i);
            JewelleryItem item = l.item;
            SaleItem si = new SaleItem();
            si.setSale(sale);
            si.setJewelleryItem(item);
            si.setTagNo(item.getTagNo());
            si.setDescription(item.getProduct().getName());
            si.setHsnCode(item.getProduct().getHsnCode());
            si.setHuid(item.getHuid());
            si.setPurity(item.getPurity().getName());
            si.setFineness(item.getPurity().getFineness());
            si.setGrossWeight(item.getGrossWeight());
            si.setStoneWeight(item.getStoneWeight());
            si.setNetWeight(item.getNetWeight());
            si.setRate(r3(l.rate));
            si.setMetalValue(r.metalValue());
            si.setWastagePercent(r3(l.wastagePercent));
            si.setWastageValue(r.wastageValue());
            si.setMakingType(l.makingType);
            si.setMakingValue(r3(l.makingValue));
            si.setMakingCharge(r.makingCharge());
            si.setStoneCharge(r.stoneCharge());
            si.setOtherCharge(r.otherCharge());
            si.setDiscount(r.discount());
            si.setTaxableValue(r.taxable());
            si.setGst(r.gst());
            si.setTotal(r.total());
            saleItems.save(si);
        }
    }

    private void movement(TenantContext.Ctx ctx, JewelleryItem item, String type, String refType, Long refId, String notes) {
        StockMovement m = new StockMovement();
        m.setCompany(ctx.company());
        m.setBranch(item.getBranch());
        m.setJewelleryItem(item);
        m.setMovementType(type);
        m.setReferenceType(refType);
        m.setReferenceId(refId);
        m.setMovementDate(GoldRateService.today());
        m.setNotes(notes);
        m.setCreatedBy(tenant.user(ctx.me()));
        movements.save(m);
    }

    private Map<String, Object> previewView(Prepared p) {
        PricingService.BillResult b = p.bill;
        Map<String, Object> m = Views.map();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < p.lines.size(); i++) {
            Line l = p.lines.get(i);
            PricingService.LineResult r = b.lines().get(i);
            Map<String, Object> x = Views.map();
            x.put("itemId", l.item.getId());
            x.put("tagNo", l.item.getTagNo());
            x.put("description", l.item.getProduct().getName());
            x.put("huid", l.item.getHuid());
            x.put("purity", l.item.getPurity().getName());
            x.put("grossWeight", l.item.getGrossWeight());
            x.put("stoneWeight", l.item.getStoneWeight());
            x.put("netWeight", l.item.getNetWeight());
            x.put("fixedPrice", positive(l.item.getMrp()));
            x.put("rate", l.rate);
            x.put("rateMissing", l.rateMissing);
            x.put("wastagePercent", l.wastagePercent);
            x.put("makingType", l.makingType);
            x.put("makingValue", l.makingValue);
            x.put("metalValue", r.metalValue());
            x.put("wastageValue", r.wastageValue());
            x.put("makingCharge", r.makingCharge());
            x.put("stoneCharge", r.stoneCharge());
            x.put("otherCharge", r.otherCharge());
            x.put("lineValue", r.lineValue());
            x.put("discount", r.discount());
            x.put("gst", r.gst());
            x.put("total", r.total());
            lines.add(x);
        }
        m.put("lines", lines);
        m.put("metalValue", b.metalValue());
        m.put("wastage", b.wastageValue());
        m.put("making", b.makingValue());
        m.put("stone", b.stoneValue());
        m.put("other", b.otherValue());
        m.put("subtotal", b.subtotal());
        m.put("discount", b.discount());
        m.put("taxable", b.taxable());
        m.put("taxMode", p.tax.effectiveMode());
        m.put("taxRate", p.tax.totalRate());
        m.put("gst", b.gst());
        m.put("cgst", b.cgst());
        m.put("sgst", b.sgst());
        m.put("roundOff", b.roundOff());
        m.put("total", b.total());
        BigDecimal ogUsed = p.oldGoldValue.min(b.total());
        m.put("oldGoldValue", p.oldGoldValue);
        m.put("oldGoldUsed", ogUsed);
        m.put("oldGoldExcess", p.oldGoldValue.subtract(ogUsed));
        m.put("payable", b.total().subtract(ogUsed));
        m.put("customer", Views.customerBrief(p.customer));
        m.put("customerAdvance", p.customer.getId() == null ? BigDecimal.ZERO : advances.customerBalance(p.customer.getId()));
        m.put("walkIn", CustomerService.isWalkIn(p.customer));
        m.put("warnings", p.warnings);
        return m;
    }
}
