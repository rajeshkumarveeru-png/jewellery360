package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Customer orders (ORDERED -> IN_PROGRESS -> READY -> DELIVERED via the final bill) and repairs
 * (RECEIVED -> IN_REPAIR -> READY -> DELIVERED). Advances for orders are customer advances linked to the order and are used
 * first on the final bill. Repair money is collected as payments linked to the repair.
 */
@Service
@RequiredArgsConstructor
public class OrdersService {
    public static final List<String> ORDER_FLOW = List.of("ORDERED", "IN_PROGRESS", "READY", "DELIVERED", "CANCELLED");
    public static final List<String> REPAIR_FLOW = List.of("RECEIVED", "IN_REPAIR", "READY", "DELIVERED", "CANCELLED");

    private final CustomOrderRepository orders;
    private final RepairOrderRepository repairs;
    private final CustomerAdvanceRepository advances;
    private final PaymentRepository payments;
    private final PurityMasterRepository purities;
    private final KarigarRepository karigars;
    private final CustomerService customers;
    private final PaymentService paymentService;
    private final NumberingService numbering;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    /* ================================================================== orders */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> orders(TenantContext.Ctx ctx, String status) {
        permissions.requirePage(ctx.me(), "SERVICES");
        String st = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (CustomOrder o : orders.list(ctx.companyId(), ctx.branchId(), "OPEN".equals(st) ? null : st)) {
            if ("OPEN".equals(st) && !List.of("ORDERED", "IN_PROGRESS", "READY").contains(o.getStatus())) continue;
            out.add(orderView(o, false));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> order(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "SERVICES");
        return orderView(loadOrder(ctx, id), true);
    }

    /** {customerId|customer, description, deliveryDate, purityId, targetWeight, makingNotes, estimate, lockRate(bool)|lockedRate, karigarId, notes, advance:{amount, mode, reference}} */
    @Transactional
    public Map<String, Object> createOrder(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        CustomOrder o = new CustomOrder();
        o.setCompany(ctx.company());
        o.setBranch(ctx.branch());
        o.setCustomer(customer(ctx, n));
        o.setOrderDate(GoldRateService.today());
        o.setOrderNo(numbering.next(ctx.company(), "ORD", o.getOrderDate()));
        o.setCreatedBy(tenant.user(ctx.me()));
        applyOrder(ctx, o, n);
        orders.save(o);
        JsonNode adv = n.get("advance");
        if (adv != null && Num.dec(adv, "amount").signum() > 0)
            paymentService.createAdvance(ctx, o.getCustomer(), Num.dec(adv, "amount"), Num.text(adv, "mode"), "RECEIPT", o, Num.text(adv, "reference", 160), null);
        audit.log(ctx, "ORDER_CREATE", "CustomOrder", o.getId(), null, Map.of("orderNo", o.getOrderNo(), "estimate", o.getEstimate()));
        return orderView(o, true);
    }

    @Transactional
    public Map<String, Object> updateOrder(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        CustomOrder o = loadOrder(ctx, id);
        if (List.of("DELIVERED", "CANCELLED").contains(o.getStatus())) throw Errors.bad("This order is closed.");
        String before = o.getStatus();
        applyOrder(ctx, o, n);
        String status = Num.text(n, "status");
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
            if (!ORDER_FLOW.contains(status)) throw Errors.bad("Unknown order status.");
            if ("DELIVERED".equals(status)) throw Errors.bad("An order is delivered by making its final bill (Billing > link the order).");
            // On cancel, any advance stays with the customer (usable on a bill or refundable from Money).
            o.setStatus(status);
        }
        audit.log(ctx, "ORDER_UPDATE", "CustomOrder", o.getId(), Map.of("status", before), Map.of("status", o.getStatus()));
        return orderView(o, true);
    }

    /** Extra advance for an order: {amount, mode, reference}. */
    @Transactional
    public Map<String, Object> orderAdvance(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        CustomOrder o = loadOrder(ctx, id);
        if (List.of("DELIVERED", "CANCELLED").contains(o.getStatus())) throw Errors.bad("This order is closed.");
        CustomerAdvance a = paymentService.createAdvance(ctx, o.getCustomer(), Num.dec(n, "amount"), Num.text(n, "mode"), "RECEIPT", o, Num.text(n, "reference", 160), null);
        audit.log(ctx, "ORDER_ADVANCE", "CustomOrder", o.getId(), null, Map.of("advanceNo", a.getAdvanceNo(), "amount", a.getAmount()));
        return orderView(o, true);
    }

    private void applyOrder(TenantContext.Ctx ctx, CustomOrder o, JsonNode n) {
        if (o.getId() == null || n.has("description")) {
            String d = Num.text(n, "description", 2000);
            if (d == null) throw Errors.bad("Describe what the customer ordered.");
            o.setDescription(d);
        }
        if (n.has("deliveryDate")) o.setDeliveryDate(Num.date(n, "deliveryDate"));
        if (n.has("purityId")) {
            Long pid = Num.id(n, "purityId");
            PurityMaster p = pid == null ? null : purities.findById(pid).orElseThrow(TenantContext::notFound);
            if (p != null) tenant.checkCompany(ctx, p.getCompany());
            o.setPurity(p);
        }
        if (n.has("targetWeight")) o.setTargetWeight(r3(Num.nonNegative(Num.dec(n, "targetWeight"), "Weight")));
        if (n.has("makingNotes")) o.setMakingNotes(Num.text(n, "makingNotes", 300));
        if (n.has("estimate")) o.setEstimate(r2(Num.nonNegative(Num.dec(n, "estimate"), "Estimate")));
        if (n.has("lockedRate")) o.setLockedRate(r3(Num.nonNegative(Num.dec(n, "lockedRate"), "Locked rate")));
        if (n.has("karigarId")) o.setKarigar(karigar(ctx, Num.id(n, "karigarId")));
        if (n.has("notes")) o.setNotes(Num.text(n, "notes", 1000));
    }

    private Map<String, Object> orderView(CustomOrder o, boolean full) {
        Map<String, Object> m = Views.map();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("date", o.getOrderDate());
        m.put("deliveryDate", o.getDeliveryDate());
        m.put("overdue", o.getDeliveryDate() != null && o.getDeliveryDate().isBefore(GoldRateService.today()) && List.of("ORDERED", "IN_PROGRESS").contains(o.getStatus()));
        m.put("customer", Views.customerBrief(o.getCustomer()));
        m.put("description", o.getDescription());
        m.put("purity", o.getPurity() == null ? null : o.getPurity().getName());
        m.put("purityId", o.getPurity() == null ? null : o.getPurity().getId());
        m.put("targetWeight", o.getTargetWeight());
        m.put("makingNotes", o.getMakingNotes());
        m.put("lockedRate", o.getLockedRate());
        m.put("estimate", o.getEstimate());
        m.put("status", o.getStatus());
        m.put("karigar", o.getKarigar() == null ? null : o.getKarigar().getName());
        m.put("karigarId", o.getKarigar() == null ? null : o.getKarigar().getId());
        List<CustomerAdvance> advs = advances.findByCustomOrderId(o.getId());
        m.put("advancePaid", advs.stream().map(CustomerAdvance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("advanceAvailable", advs.stream().map(CustomerAdvance::getBalanceAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("balanceEstimate", o.getEstimate().subtract((BigDecimal) m.get("advancePaid")).max(BigDecimal.ZERO));
        if (full) {
            m.put("notes", o.getNotes());
            m.put("advances", advs.stream().map(a -> Map.of("advanceNo", a.getAdvanceNo(), "date", a.getAdvanceDate(), "amount", a.getAmount(), "balance", a.getBalanceAmount())).toList());
        }
        return m;
    }

    private CustomOrder loadOrder(TenantContext.Ctx ctx, Long id) {
        CustomOrder o = orders.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, o.getCompany(), o.getBranch());
        return o;
    }

    /* ================================================================== repairs */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> repairs(TenantContext.Ctx ctx, String status) {
        permissions.requirePage(ctx.me(), "SERVICES");
        String st = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RepairOrder r : repairs.list(ctx.companyId(), ctx.branchId(), "OPEN".equals(st) ? null : st)) {
            if ("OPEN".equals(st) && !List.of("RECEIVED", "IN_REPAIR", "READY").contains(r.getStatus())) continue;
            out.add(repairView(r, false));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> repair(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "SERVICES");
        return repairView(loadRepair(ctx, id), true);
    }

    /** {customerId|customer, itemDescription, itemTag, huid, grossWeight, problem, expectedDate, estimate, karigarId, notes, advance:{amount, mode}} */
    @Transactional
    public Map<String, Object> createRepair(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        RepairOrder r = new RepairOrder();
        r.setCompany(ctx.company());
        r.setBranch(ctx.branch());
        r.setCustomer(customer(ctx, n));
        r.setReceivedDate(GoldRateService.today());
        r.setOrderNo(numbering.next(ctx.company(), "REP", r.getReceivedDate()));
        r.setCreatedBy(tenant.user(ctx.me()));
        applyRepair(ctx, r, n);
        repairs.save(r);
        JsonNode adv = n.get("advance");
        if (adv != null && Num.dec(adv, "amount").signum() > 0) collectRepair(ctx, r, Num.dec(adv, "amount"), Num.text(adv, "mode"), "Advance for repair");
        audit.log(ctx, "REPAIR_CREATE", "RepairOrder", r.getId(), null, Map.of("orderNo", r.getOrderNo(), "item", r.getItemDescription()));
        return repairView(r, true);
    }

    /** Status and details: {status, finalAmount, karigarId, expectedDate, notes, ...}. DELIVERED can take the final payment {payment:{amount, mode}}. */
    @Transactional
    public Map<String, Object> updateRepair(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        RepairOrder r = loadRepair(ctx, id);
        if (List.of("DELIVERED", "CANCELLED").contains(r.getStatus())) throw Errors.bad("This repair is closed.");
        String before = r.getStatus();
        applyRepair(ctx, r, n);
        if (n.has("finalAmount")) r.setFinalAmount(r2(Num.nonNegative(Num.dec(n, "finalAmount"), "Charge")));
        String status = Num.text(n, "status");
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
            if (!REPAIR_FLOW.contains(status)) throw Errors.bad("Unknown repair status.");
            r.setStatus(status);
            if ("DELIVERED".equals(status)) {
                if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
                r.setDeliveredDate(GoldRateService.today());
                BigDecimal charge = r.getFinalAmount().signum() > 0 ? r.getFinalAmount() : r.getEstimate();
                r.setFinalAmount(charge);
                JsonNode pay = n.get("payment");
                if (pay != null && Num.dec(pay, "amount").signum() > 0) collectRepair(ctx, r, Num.dec(pay, "amount"), Num.text(pay, "mode"), "Repair delivered");
                BigDecimal due = charge.subtract(repairPaid(r));
                if (due.signum() > 0) throw Errors.bad("₹" + r2(due).toPlainString() + " is still due on this repair. Take the payment to deliver it.");
            }
        }
        audit.log(ctx, "REPAIR_UPDATE", "RepairOrder", r.getId(), Map.of("status", before), Map.of("status", r.getStatus(), "finalAmount", r.getFinalAmount()));
        return repairView(r, true);
    }

    private void applyRepair(TenantContext.Ctx ctx, RepairOrder r, JsonNode n) {
        if (r.getId() == null || n.has("itemDescription")) {
            String d = Num.text(n, "itemDescription", 300);
            if (d == null) throw Errors.bad("Describe the piece received for repair.");
            r.setItemDescription(d);
        }
        if (r.getId() == null || n.has("problem")) {
            String p = Num.text(n, "problem", 2000);
            if (p == null) throw Errors.bad("Describe the work to be done.");
            r.setProblem(p);
        }
        if (n.has("itemTag")) r.setItemTag(Num.text(n, "itemTag", 80));
        if (n.has("huid")) r.setHuid(Optional.ofNullable(Num.text(n, "huid", 20)).map(s -> s.toUpperCase(Locale.ROOT)).orElse(null));
        if (n.has("grossWeight")) r.setGrossWeight(r3(Num.nonNegative(Num.dec(n, "grossWeight"), "Weight")));
        if (n.has("expectedDate")) r.setExpectedDate(Num.date(n, "expectedDate"));
        if (n.has("estimate")) r.setEstimate(r2(Num.nonNegative(Num.dec(n, "estimate"), "Estimate")));
        if (n.has("karigarId")) r.setKarigar(karigar(ctx, Num.id(n, "karigarId")));
        if (n.has("notes")) r.setNotes(Num.text(n, "notes", 1000));
    }

    private void collectRepair(TenantContext.Ctx ctx, RepairOrder r, BigDecimal amount, String mode, String note) {
        paymentService.record(ctx, Payment.RECEIPT, PaymentService.moneyMode(mode), amount, x -> { x.setCustomer(r.getCustomer()); x.setRepairOrder(r); }, r.getOrderNo(), note);
    }

    private BigDecimal repairPaid(RepairOrder r) {
        if (r.getId() == null) return BigDecimal.ZERO;
        return payments.findByRepairOrderIdOrderById(r.getId()).stream().map(p -> p.getAmount().multiply(BigDecimal.valueOf(p.direction()))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> repairView(RepairOrder r, boolean full) {
        Map<String, Object> m = Views.map();
        m.put("id", r.getId());
        m.put("orderNo", r.getOrderNo());
        m.put("receivedDate", r.getReceivedDate());
        m.put("expectedDate", r.getExpectedDate());
        m.put("overdue", r.getExpectedDate() != null && r.getExpectedDate().isBefore(GoldRateService.today()) && List.of("RECEIVED", "IN_REPAIR").contains(r.getStatus()));
        m.put("customer", Views.customerBrief(r.getCustomer()));
        m.put("itemDescription", r.getItemDescription());
        m.put("itemTag", r.getItemTag());
        m.put("huid", r.getHuid());
        m.put("grossWeight", r.getGrossWeight());
        m.put("problem", r.getProblem());
        m.put("estimate", r.getEstimate());
        m.put("finalAmount", r.getFinalAmount());
        BigDecimal paid = repairPaid(r);
        BigDecimal charge = r.getFinalAmount().signum() > 0 ? r.getFinalAmount() : r.getEstimate();
        m.put("paid", paid);
        m.put("balance", charge.subtract(paid).max(BigDecimal.ZERO));
        m.put("status", r.getStatus());
        m.put("karigar", r.getKarigar() == null ? null : r.getKarigar().getName());
        m.put("karigarId", r.getKarigar() == null ? null : r.getKarigar().getId());
        m.put("deliveredDate", r.getDeliveredDate());
        if (full) {
            m.put("notes", r.getNotes());
            m.put("payments", payments.findByRepairOrderIdOrderById(r.getId()).stream().map(Views::payment).toList());
        }
        return m;
    }

    private RepairOrder loadRepair(TenantContext.Ctx ctx, Long id) {
        RepairOrder r = repairs.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, r.getCompany(), r.getBranch());
        return r;
    }

    /* ================================================================== helpers */

    private Customer customer(TenantContext.Ctx ctx, JsonNode n) {
        Long id = Num.id(n, "customerId");
        if (id != null) return customers.get(ctx, id);
        JsonNode c = n.get("customer");
        if (c != null && Num.text(c, "name") != null) return customers.get(ctx, (Long) customers.create(ctx, c).get("id"));
        throw Errors.bad("Pick the customer.");
    }

    private Karigar karigar(TenantContext.Ctx ctx, Long id) {
        if (id == null) return null;
        Karigar k = karigars.findById(id).orElseThrow(TenantContext::notFound);
        tenant.checkCompany(ctx, k.getCompany());
        return k;
    }

    public static boolean dueSoon(LocalDate d) { return d != null && !d.isAfter(GoldRateService.today().plusDays(2)); }
}
