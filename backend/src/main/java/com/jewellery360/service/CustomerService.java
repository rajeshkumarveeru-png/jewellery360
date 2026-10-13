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
import java.time.LocalDate;
import java.util.*;

import static com.jewellery360.support.Num.*;

/** Customers, their ledger ("owes ₹X" / "has ₹X advance"), their jewellery timeline and payment collection. */
@Service
@RequiredArgsConstructor
public class CustomerService {
    public static final String WALK_IN = "Walk-in Customer";

    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SaleReturnRepository returns;
    private final PaymentRepository payments;
    private final CustomerAdvanceRepository advances;
    private final OldGoldTransactionRepository oldGold;
    private final RepairOrderRepository repairs;
    private final CustomOrderRepository orders;
    private final PaymentService paymentService;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(TenantContext.Ctx ctx, String q, int limit) {
        permissions.requirePage(ctx.me(), "CUSTOMERS");
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Map<Long, BigDecimal> owes = toMap(sales.balancesByCustomer(ctx.companyId()));
        Map<Long, BigDecimal> adv = toMap(advances.balancesByCustomer(ctx.companyId()));
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] r : sales.purchaseStatsByCustomer(ctx.companyId())) stats.put((Long) r[0], r);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Customer c : customers.search(ctx.companyId(), like, PageRequest.of(0, Math.max(1, Math.min(limit, 500))))) {
            Map<String, Object> m = Views.customer(c);
            m.put("owes", owes.getOrDefault(c.getId(), BigDecimal.ZERO));
            m.put("advance", adv.getOrDefault(c.getId(), BigDecimal.ZERO));
            Object[] st = stats.get(c.getId());
            m.put("bills", st == null ? 0 : st[1]);
            m.put("purchases", st == null ? BigDecimal.ZERO : st[2]);
            m.put("lastVisit", st == null ? null : st[3]);
            m.put("walkIn", WALK_IN.equals(c.getName()) && c.getPhone() == null);
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> create(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireWrite(ctx.me(), "CUSTOMERS");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Customer c = new Customer();
        c.setCompany(ctx.company());
        c.setBranch(ctx.branch());
        apply(ctx, c, n, true);
        customers.save(c);
        audit.log(ctx, "CUSTOMER_CREATE", "Customer", c.getId(), null, Map.of("name", c.getName(), "phone", Objects.toString(c.getPhone(), "")));
        return Views.customer(c);
    }

    @Transactional
    public Map<String, Object> update(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireWrite(ctx.me(), "CUSTOMERS");
        Customer c = get(ctx, id);
        Map<String, Object> before = Views.customer(c);
        apply(ctx, c, n, false);
        audit.log(ctx, "CUSTOMER_UPDATE", "Customer", c.getId(), before, Views.customer(c));
        return Views.customer(c);
    }

    @Transactional
    public void deactivate(TenantContext.Ctx ctx, Long id) {
        permissions.requireManage(ctx.me(), "CUSTOMERS");
        Customer c = get(ctx, id);
        if (sales.customerBalance(c.getId()).signum() != 0 || advances.customerBalance(c.getId()).signum() != 0)
            throw Errors.bad("This customer still has dues or an advance. Settle them before removing the customer.");
        c.setActive(false);
        audit.log(ctx, "CUSTOMER_DEACTIVATE", "Customer", c.getId(), null, null);
    }

    private void apply(TenantContext.Ctx ctx, Customer c, JsonNode n, boolean creating) {
        String name = Num.text(n, "name", 180);
        if (creating || n.has("name")) {
            if (name == null || name.length() < 2) throw Errors.bad("Enter the customer's name.");
            c.setName(name);
        }
        if (creating || n.has("phone")) {
            String phone = normalizePhone(Num.text(n, "phone", 25));
            if (phone != null && !Num.bool(n, "allowDuplicate", false)) {
                for (Customer other : customers.findByCompanyIdAndPhone(ctx.companyId(), phone)) {
                    if (other.isActive() && !other.getId().equals(c.getId()))
                        throw Errors.conflict("This phone number already belongs to " + other.getName() + ". Pick that customer instead.");
                }
            }
            c.setPhone(phone);
        }
        if (creating || n.has("email")) c.setEmail(Num.text(n, "email", 180));
        if (creating || n.has("address")) c.setAddress(Num.text(n, "address", 600));
        if (creating || n.has("gstin")) {
            String g = Num.text(n, "gstin", 30);
            if (g != null && !g.toUpperCase(Locale.ROOT).matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")) throw Errors.bad("GSTIN must be 15 characters, e.g. 33ABCDE1234F1Z5.");
            c.setGstin(g == null ? null : g.toUpperCase(Locale.ROOT));
        }
        if (creating || n.has("pan")) {
            String pan = Num.text(n, "pan", 20);
            if (pan != null && !pan.toUpperCase(Locale.ROOT).matches("^[A-Z]{5}[0-9]{4}[A-Z]$")) throw Errors.bad("PAN must look like ABCDE1234F.");
            c.setPan(pan == null ? null : pan.toUpperCase(Locale.ROOT));
        }
        if (creating || n.has("birthday")) c.setBirthday(Num.date(n, "birthday"));
        if (creating || n.has("anniversary")) c.setAnniversary(Num.date(n, "anniversary"));
        if (creating || n.has("notes")) c.setNotes(Num.text(n, "notes", 2000));
    }

    static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+91")) digits = digits.substring(3);
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (!digits.matches("\\+?[0-9]{7,15}")) throw Errors.bad("Enter a valid phone number.");
        return digits;
    }

    public Customer get(TenantContext.Ctx ctx, Long id) {
        Customer c = customers.findById(id).orElseThrow(TenantContext::notFound);
        tenant.checkCompany(ctx, c.getCompany());
        return c;
    }

    /** The company's walk-in customer (created on first use). Walk-in bills must be fully paid. */
    @Transactional
    public Customer walkIn(TenantContext.Ctx ctx) {
        return customers.search(ctx.companyId(), "%walk-in customer%", PageRequest.of(0, 5)).stream()
                .filter(c -> WALK_IN.equals(c.getName()) && c.getPhone() == null).findFirst().orElseGet(() -> {
                    Customer c = new Customer();
                    c.setCompany(ctx.company());
                    c.setBranch(ctx.branch());
                    c.setName(WALK_IN);
                    c.setNotes("Used for bills without customer details. Walk-in bills must be paid in full.");
                    return customers.save(c);
                });
    }

    public static boolean isWalkIn(Customer c) { return WALK_IN.equals(c.getName()) && c.getPhone() == null; }

    /** Profile: details, what they owe, their advance, the two ledgers and the full jewellery timeline. */
    @Transactional(readOnly = true)
    public Map<String, Object> profile(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "CUSTOMERS");
        Customer c = get(ctx, id);
        List<Sale> bills = sales.findByCustomerIdOrderBySaleDateDescIdDesc(id);
        List<Payment> pays = payments.findByCustomerIdOrderByPaymentDateAscIdAsc(id);
        List<SaleReturn> rets = returns.forCustomer(id);
        List<CustomerAdvance> advs = advances.findByCustomerIdOrderByAdvanceDateAscIdAsc(id);

        // Bills ledger: what the customer owes on bills.
        List<Map<String, Object>> ledger = new ArrayList<>();
        for (Sale s : bills) {
            if (Sale.DRAFT.equals(s.getStatus()) || Sale.CANCELLED.equals(s.getStatus())) continue;
            ledger.add(entry(s.getSaleDate(), s.getCreatedAt(), "Bill", s.getInvoiceNo(), s.getTotal(), BigDecimal.ZERO, s.getId()));
        }
        for (SaleReturn r : rets) {
            if (Sale.CANCELLED.equals(r.getSale().getStatus())) continue;
            ledger.add(entry(r.getReturnDate(), r.getCreatedAt(), "Return", r.getReturnNo() + " (" + r.getSale().getInvoiceNo() + ")", BigDecimal.ZERO, r.getAmount(), r.getSale().getId()));
        }
        for (Payment p : pays) {
            if (p.getSale() == null || Sale.CANCELLED.equals(p.getSale().getStatus())) continue;
            String what = switch (p.getPaymentMode()) {
                case Payment.MODE_ADVANCE -> Payment.REFUND.equals(p.getPaymentType()) ? "Refund kept as advance" : "Advance adjusted";
                case Payment.MODE_OLD_GOLD -> "Old gold exchanged";
                default -> Payment.REFUND.equals(p.getPaymentType()) ? "Refund (" + p.getPaymentMode() + ")" : "Payment (" + p.getPaymentMode() + ")";
            };
            boolean refund = Payment.REFUND.equals(p.getPaymentType());
            ledger.add(entry(p.getPaymentDate(), p.getCreatedAt(), what, p.getPaymentNo() + " · " + p.getSale().getInvoiceNo(),
                    refund ? p.getAmount() : BigDecimal.ZERO, refund ? BigDecimal.ZERO : p.getAmount(), p.getSale().getId()));
        }
        ledger.sort(Comparator.comparing((Map<String, Object> e) -> (LocalDate) e.get("date")).thenComparing(e -> (java.time.Instant) e.get("at")));
        BigDecimal running = BigDecimal.ZERO;
        for (Map<String, Object> e : ledger) {
            running = running.add((BigDecimal) e.get("debit")).subtract((BigDecimal) e.get("credit"));
            e.put("balance", running);
            e.remove("at");
        }

        // Advance ledger.
        List<Map<String, Object>> advanceRows = new ArrayList<>();
        for (CustomerAdvance a : advs) {
            Map<String, Object> m = Views.map();
            m.put("id", a.getId());
            m.put("advanceNo", a.getAdvanceNo());
            m.put("date", a.getAdvanceDate());
            m.put("source", a.getSource());
            m.put("orderNo", a.getCustomOrder() == null ? null : a.getCustomOrder().getOrderNo());
            m.put("amount", a.getAmount());
            m.put("used", a.getUsedAmount());
            m.put("refunded", a.getRefundedAmount());
            m.put("balance", a.getBalanceAmount());
            m.put("status", a.getStatus());
            advanceRows.add(m);
        }

        BigDecimal owes = sales.customerBalance(id);
        BigDecimal advance = advances.customerBalance(id);
        Map<String, Object> summary = Views.map();
        summary.put("owes", owes);
        summary.put("advance", advance);
        summary.put("net", owes.subtract(advance));
        summary.put("message", owes.signum() > 0 ? "Customer owes ₹" + r2(owes).toPlainString()
                : advance.signum() > 0 ? "Customer has ₹" + r2(advance).toPlainString() + " advance" : "No dues");
        List<Sale> valid = bills.stream().filter(s -> !Sale.DRAFT.equals(s.getStatus()) && !Sale.CANCELLED.equals(s.getStatus())).toList();
        summary.put("bills", valid.size());
        summary.put("purchases", valid.stream().map(s -> s.getTotal().subtract(s.getReturnedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.put("lastVisit", valid.isEmpty() ? null : valid.get(0).getSaleDate());
        summary.put("loyaltyPoints", valid.stream().map(s -> s.getTotal().subtract(s.getReturnedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(1000), 0, java.math.RoundingMode.DOWN));

        Map<String, Object> out = Views.map();
        out.put("customer", Views.customer(c));
        out.put("summary", summary);
        out.put("bills", bills.stream().map(Views::saleSummary).toList());
        out.put("ledger", ledger);
        out.put("advances", advanceRows);
        out.put("timeline", timeline(c, bills, pays, rets, advs));
        out.put("unpaidBills", sales.unpaidForCustomer(id).stream().map(Views::saleSummary).toList());
        return out;
    }

    private Map<String, Object> entry(LocalDate date, java.time.Instant at, String what, String ref, BigDecimal debit, BigDecimal credit, Long saleId) {
        Map<String, Object> m = Views.map();
        m.put("date", date);
        m.put("at", at);
        m.put("type", what);
        m.put("reference", ref);
        m.put("debit", debit);
        m.put("credit", credit);
        m.put("saleId", saleId);
        return m;
    }

    /** Customer jewellery timeline: purchases (with the pieces), returns, payments, advances, old gold, repairs, orders. */
    private List<Map<String, Object>> timeline(Customer c, List<Sale> bills, List<Payment> pays, List<SaleReturn> rets, List<CustomerAdvance> advs) {
        List<Map<String, Object>> t = new ArrayList<>();
        for (Sale s : bills) {
            if (Sale.DRAFT.equals(s.getStatus())) continue;
            List<String> pieces = saleItems.findBySaleIdOrderById(s.getId()).stream()
                    .map(i -> i.getDescription() + " (" + i.getPurity() + ", " + i.getNetWeight().stripTrailingZeros().toPlainString() + " g)").toList();
            t.add(event(s.getSaleDate(), "PURCHASE", "Bought " + String.join(", ", pieces), s.getInvoiceNo(), s.getTotal(), Views.billLabel(s)));
        }
        for (SaleReturn r : rets) t.add(event(r.getReturnDate(), "RETURN", "Returned items from " + r.getSale().getInvoiceNo(), r.getReturnNo(), r.getAmount(), r.getReason()));
        for (Payment p : pays) {
            if (p.getSale() != null || p.getAdvance() != null && Payment.ADVANCE.equals(p.getPaymentType())) continue;
            if (p.getRepairOrder() != null) t.add(event(p.getPaymentDate(), "PAYMENT", "Paid for repair " + p.getRepairOrder().getOrderNo(), p.getPaymentNo(), p.getAmount(), p.getPaymentMode()));
            else if (p.getOldGold() != null && Payment.PAYOUT.equals(p.getPaymentType())) t.add(event(p.getPaymentDate(), "OLD_GOLD", "Paid for old gold", p.getPaymentNo(), p.getAmount(), p.getPaymentMode()));
            else if (Payment.REFUND.equals(p.getPaymentType())) t.add(event(p.getPaymentDate(), "REFUND", "Advance refunded", p.getPaymentNo(), p.getAmount(), p.getPaymentMode()));
        }
        for (CustomerAdvance a : advs) t.add(event(a.getAdvanceDate(), "ADVANCE", "RETURN".equals(a.getSource()) ? "Credit from a return" :
                a.getCustomOrder() == null ? "Advance paid" : "Advance for order " + a.getCustomOrder().getOrderNo(), a.getAdvanceNo(), a.getAmount(), a.getStatus()));
        for (OldGoldTransaction o : oldGold.findByCustomerIdOrderByTransactionDateDesc(c.getId()))
            t.add(event(o.getTransactionDate(), "OLD_GOLD", ("EXCHANGE".equals(o.getTransactionType()) ? "Old gold exchanged: " : "Old gold sold to shop: ")
                    + o.getTotalNetWeight().stripTrailingZeros().toPlainString() + " g net", o.getTransactionNo(), o.getTotalAmount(), o.getStatus()));
        for (RepairOrder r : repairs.findByCustomerIdOrderByReceivedDateDesc(c.getId()))
            t.add(event(r.getReceivedDate(), "REPAIR", "Repair: " + r.getItemDescription() + " - " + r.getProblem(), r.getOrderNo(),
                    r.getFinalAmount().signum() > 0 ? r.getFinalAmount() : r.getEstimate(), r.getStatus()));
        for (CustomOrder o : orders.findByCustomerIdOrderByOrderDateDesc(c.getId()))
            t.add(event(o.getOrderDate(), "ORDER", "Order: " + o.getDescription(), o.getOrderNo(), o.getEstimate(), o.getStatus()));
        t.sort(Comparator.comparing((Map<String, Object> e) -> (LocalDate) e.get("date")).reversed());
        return t;
    }

    private Map<String, Object> event(LocalDate date, String kind, String title, String ref, BigDecimal amount, String status) {
        Map<String, Object> m = Views.map();
        m.put("date", date);
        m.put("kind", kind);
        m.put("title", title);
        m.put("reference", ref);
        m.put("amount", amount);
        m.put("status", status);
        return m;
    }

    /** Collect money from a customer against dues (oldest bills first); the rest can be kept as advance. */
    @Transactional
    public Map<String, Object> collect(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireWrite(ctx.me(), "MONEY");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Customer c = get(ctx, id);
        BigDecimal amount = Num.dec(n, "amount");
        Map<String, Object> out = paymentService.collect(ctx, c, amount, Num.text(n, "mode"), Num.text(n, "reference", 160), Num.bool(n, "excessToAdvance", false));
        audit.log(ctx, "CUSTOMER_PAYMENT", "Customer", c.getId(), null, Map.of("amount", amount, "mode", Objects.toString(Num.text(n, "mode"), "CASH")));
        out.put("summary", Map.of("owes", sales.customerBalance(id), "advance", advances.customerBalance(id)));
        return out;
    }

    private static Map<Long, BigDecimal> toMap(List<Object[]> rows) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put((Long) r[0], (BigDecimal) r[1]);
        return m;
    }
}
