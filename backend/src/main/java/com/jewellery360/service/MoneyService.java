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

/** Money page: payments register, customer advances, expenses and the end-of-day cash count. */
@Service
@RequiredArgsConstructor
public class MoneyService {
    public static final List<String> EXPENSE_CATEGORIES = List.of("Rent", "Salary", "Electricity", "Hallmarking", "Polishing", "Packaging",
            "Transport", "Tea / Refreshments", "Repairs & Maintenance", "Marketing", "Bank Charges", "Other");

    private final PaymentRepository payments;
    private final CustomerAdvanceRepository advances;
    private final ExpenseRepository expenses;
    private final DayCloseRepository dayCloses;
    private final SaleRepository sales;
    private final CustomerService customers;
    private final PaymentService paymentService;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    /* ------------------------------------------------------------------ payments */

    @Transactional(readOnly = true)
    public Map<String, Object> payments(TenantContext.Ctx ctx, LocalDate from, LocalDate to, String type, String mode) {
        permissions.requirePage(ctx.me(), "MONEY");
        LocalDate f = from == null ? GoldRateService.today() : from, t = to == null ? f : to;
        if (t.isBefore(f)) throw Errors.bad("'To' date is before 'from' date.");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> byMode = new TreeMap<>();
        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO;
        for (Payment p : payments.between(ctx.companyId(), ctx.branchId(), f, t)) {
            if (type != null && !type.isBlank() && !type.equalsIgnoreCase(p.getPaymentType())) continue;
            if (mode != null && !mode.isBlank() && !mode.equalsIgnoreCase(p.getPaymentMode())) continue;
            rows.add(Views.payment(p));
            if (!p.isMoney()) continue;
            BigDecimal signed = p.getAmount().multiply(BigDecimal.valueOf(p.direction()));
            byMode.merge(p.getPaymentMode(), signed, BigDecimal::add);
            if (p.direction() > 0) in = in.add(p.getAmount()); else out = out.add(p.getAmount());
        }
        Map<String, Object> m = Views.map();
        m.put("from", f);
        m.put("to", t);
        m.put("moneyIn", r2(in));
        m.put("moneyOut", r2(out));
        m.put("net", r2(in.subtract(out)));
        m.put("byMode", byMode);
        m.put("payments", rows);
        return m;
    }

    /* ------------------------------------------------------------------ advances */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> advances(TenantContext.Ctx ctx, boolean openOnly) {
        permissions.requirePage(ctx.me(), "MONEY");
        return advances.list(ctx.companyId(), ctx.branchId(), openOnly).stream().map(MoneyService::advanceView).toList();
    }

    /** {customerId, amount, mode, reference, notes} */
    @Transactional
    public Map<String, Object> createAdvance(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireWrite(ctx.me(), "MONEY");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Customer c = customers.get(ctx, Num.requireId(n, "customerId", "customer"));
        if (CustomerService.isWalkIn(c)) throw Errors.bad("Advances need a named customer.");
        CustomerAdvance a = paymentService.createAdvance(ctx, c, Num.dec(n, "amount"), Num.text(n, "mode"), "RECEIPT", null,
                Num.text(n, "reference", 160), Num.text(n, "notes", 1000));
        audit.log(ctx, "ADVANCE_CREATE", "CustomerAdvance", a.getId(), null, Map.of("advanceNo", a.getAdvanceNo(), "amount", a.getAmount()));
        return advanceView(a);
    }

    /** {amount, mode, reference} */
    @Transactional
    public Map<String, Object> refundAdvance(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireWrite(ctx.me(), "MONEY");
        permissions.requirePower(ctx.me(), "CANCEL_RETURN");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        CustomerAdvance a = advances.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, a.getCompany(), a.getBranch());
        Payment p = paymentService.refundAdvance(ctx, a, Num.dec(n, "amount"), Num.text(n, "mode"), Num.text(n, "reference", 160));
        audit.log(ctx, "ADVANCE_REFUND", "CustomerAdvance", a.getId(), null, Map.of("paymentNo", p.getPaymentNo(), "amount", p.getAmount()));
        return advanceView(advances.findById(id).orElseThrow());
    }

    static Map<String, Object> advanceView(CustomerAdvance a) {
        Map<String, Object> m = Views.map();
        m.put("id", a.getId());
        m.put("advanceNo", a.getAdvanceNo());
        m.put("date", a.getAdvanceDate());
        m.put("customer", Views.customerBrief(a.getCustomer()));
        m.put("source", a.getSource());
        m.put("orderNo", a.getCustomOrder() == null ? null : a.getCustomOrder().getOrderNo());
        m.put("amount", a.getAmount());
        m.put("used", a.getUsedAmount());
        m.put("refunded", a.getRefundedAmount());
        m.put("balance", a.getBalanceAmount());
        m.put("status", a.getStatus());
        m.put("notes", a.getNotes());
        return m;
    }

    /* ------------------------------------------------------------------ expenses */

    @Transactional(readOnly = true)
    public Map<String, Object> expenses(TenantContext.Ctx ctx, LocalDate from, LocalDate to) {
        permissions.requirePage(ctx.me(), "MONEY");
        LocalDate t = to == null ? GoldRateService.today() : to, f = from == null ? t.withDayOfMonth(1) : from;
        List<Expense> list = expenses.between(ctx.companyId(), ctx.branchId(), f, t);
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        for (Expense e : list) byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        Set<String> cats = new LinkedHashSet<>(EXPENSE_CATEGORIES);
        cats.addAll(expenses.categories(ctx.companyId()));
        Map<String, Object> m = Views.map();
        m.put("from", f);
        m.put("to", t);
        m.put("total", r2(list.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        m.put("byCategory", byCategory);
        m.put("categories", cats);
        m.put("expenses", list.stream().map(MoneyService::expenseView).toList());
        return m;
    }

    /** {date, category, title, amount, mode, reference, notes} */
    @Transactional
    public Map<String, Object> createExpense(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireWrite(ctx.me(), "MONEY");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Expense e = new Expense();
        e.setCompany(ctx.company());
        e.setBranch(ctx.branch());
        LocalDate d = Optional.ofNullable(Num.date(n, "date")).orElse(GoldRateService.today());
        if (d.isAfter(GoldRateService.today())) throw Errors.bad("Expense date can't be in the future.");
        dayCloses.findByBranchIdAndCloseDate(ctx.branchId(), d).ifPresent(x -> { throw Errors.bad("The day " + d + " is already closed."); });
        e.setExpenseDate(d);
        e.setCategory(Optional.ofNullable(Num.text(n, "category", 60)).orElse("Other"));
        String title = Num.text(n, "title", 180);
        e.setTitle(title == null ? e.getCategory() : title);
        BigDecimal amt = Num.dec(n, "amount");
        if (amt.signum() <= 0) throw Errors.bad("Amount must be more than 0.");
        e.setAmount(r2(amt));
        e.setPaymentMode(PaymentService.moneyMode(Num.text(n, "mode")));
        e.setReference(Num.text(n, "reference", 160));
        e.setNotes(Num.text(n, "notes", 1000));
        e.setCreatedBy(tenant.user(ctx.me()));
        expenses.save(e);
        audit.log(ctx, "EXPENSE_CREATE", "Expense", e.getId(), null, Map.of("title", e.getTitle(), "amount", e.getAmount()));
        return expenseView(e);
    }

    @Transactional
    public void deleteExpense(TenantContext.Ctx ctx, Long id) {
        permissions.requireManage(ctx.me(), "MONEY");
        Expense e = expenses.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, e.getCompany(), e.getBranch());
        if (dayCloses.findByBranchIdAndCloseDate(e.getBranch().getId(), e.getExpenseDate()).isPresent())
            throw Errors.bad("That day is already closed; the expense can't be removed.");
        expenses.delete(e);
        audit.log(ctx, "EXPENSE_DELETE", "Expense", id, Map.of("title", e.getTitle(), "amount", e.getAmount()), null);
    }

    static Map<String, Object> expenseView(Expense e) {
        Map<String, Object> m = Views.map();
        m.put("id", e.getId());
        m.put("date", e.getExpenseDate());
        m.put("category", e.getCategory());
        m.put("title", e.getTitle());
        m.put("amount", e.getAmount());
        m.put("mode", e.getPaymentMode());
        m.put("reference", e.getReference());
        m.put("notes", e.getNotes());
        m.put("branch", e.getBranch().getName());
        m.put("createdBy", e.getCreatedBy() == null ? null : e.getCreatedBy().getUsername());
        return m;
    }

    /* ------------------------------------------------------------------ day close */

    /** Figures for the day (default today) without saving; includes the saved close if there is one. */
    @Transactional(readOnly = true)
    public Map<String, Object> dayClosePreview(TenantContext.Ctx ctx, LocalDate date) {
        permissions.requirePage(ctx.me(), "MONEY");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        LocalDate d = date == null ? GoldRateService.today() : date;
        Optional<DayClose> saved = dayCloses.findByBranchIdAndCloseDate(ctx.branchId(), d);
        if (saved.isPresent()) {
            Map<String, Object> m = closeView(saved.get());
            m.put("closed", true);
            return m;
        }
        DayClose c = compute(ctx, d, null);
        Map<String, Object> m = closeView(c);
        m.put("closed", false);
        return m;
    }

    /** {date, actualCash, openingCash (only when there is no earlier close), notes} */
    @Transactional
    public Map<String, Object> closeDay(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireWrite(ctx.me(), "MONEY");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        LocalDate d = Optional.ofNullable(Num.date(n, "date")).orElse(GoldRateService.today());
        if (d.isAfter(GoldRateService.today())) throw Errors.bad("You can't close a future day.");
        Optional<DayClose> existing = dayCloses.findByBranchIdAndCloseDate(ctx.branchId(), d);
        if (existing.isPresent() && !ctx.admin()) throw Errors.conflict("This day is already closed. Ask the owner to re-close it.");
        if (!n.has("actualCash")) throw Errors.bad("Count the cash in the drawer and enter it.");
        DayClose c = compute(ctx, d, Num.decOrNull(n, "openingCash"));
        DayClose target = existing.orElse(c);
        if (existing.isPresent()) copy(c, target);
        target.setActualCash(r2(Num.nonNegative(Num.dec(n, "actualCash"), "Cash counted")));
        target.setDifference(r2(target.getActualCash().subtract(target.getExpectedCash())));
        target.setNotes(Num.text(n, "notes", 1000));
        target.setCreatedBy(tenant.user(ctx.me()));
        dayCloses.save(target);
        audit.log(ctx, existing.isPresent() ? "DAY_RECLOSE" : "DAY_CLOSE", "DayClose", target.getId(), null,
                Map.of("date", d, "expected", target.getExpectedCash(), "actual", target.getActualCash(), "difference", target.getDifference()));
        Map<String, Object> m = closeView(target);
        m.put("closed", true);
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> dayCloseHistory(TenantContext.Ctx ctx) {
        permissions.requirePage(ctx.me(), "MONEY");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        return dayCloses.findTop30ByBranchIdOrderByCloseDateDesc(ctx.branchId()).stream().map(MoneyService::closeView).toList();
    }

    private DayClose compute(TenantContext.Ctx ctx, LocalDate d, BigDecimal openingOverride) {
        DayClose c = new DayClose();
        c.setCompany(ctx.company());
        c.setBranch(ctx.branch());
        c.setCloseDate(d);
        Optional<DayClose> prev = dayCloses.findFirstByBranchIdAndCloseDateBeforeOrderByCloseDateDesc(ctx.branchId(), d);
        c.setOpeningCash(r2(prev.map(DayClose::getActualCash).orElse(openingOverride == null ? BigDecimal.ZERO : openingOverride)));
        BigDecimal cashIn = BigDecimal.ZERO, cashOut = BigDecimal.ZERO, upi = BigDecimal.ZERO, card = BigDecimal.ZERO, bank = BigDecimal.ZERO,
                adv = BigDecimal.ZERO, refunds = BigDecimal.ZERO;
        for (Payment p : payments.between(ctx.companyId(), ctx.branchId(), d, d)) {
            if (!p.isMoney()) continue;
            BigDecimal signed = p.getAmount().multiply(BigDecimal.valueOf(p.direction()));
            switch (p.getPaymentMode()) {
                case "CASH" -> { if (p.direction() > 0) cashIn = cashIn.add(p.getAmount()); else cashOut = cashOut.add(p.getAmount()); }
                case "UPI" -> upi = upi.add(signed);
                case "CARD" -> card = card.add(signed);
                default -> bank = bank.add(signed); // BANK, CHEQUE
            }
            if (Payment.ADVANCE.equals(p.getPaymentType())) adv = adv.add(p.getAmount());
            if (Payment.REFUND.equals(p.getPaymentType())) refunds = refunds.add(p.getAmount());
        }
        BigDecimal cashExp = BigDecimal.ZERO;
        for (Expense e : expenses.between(ctx.companyId(), ctx.branchId(), d, d)) {
            if ("CASH".equals(e.getPaymentMode())) cashExp = cashExp.add(e.getAmount());
            else bank = bank.subtract(e.getAmount());
        }
        BigDecimal salesTotal = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (Sale s : sales.completedBetween(ctx.companyId(), ctx.branchId(), d, d)) {
            salesTotal = salesTotal.add(s.getTotal());
            credit = credit.add(s.balance().max(BigDecimal.ZERO));
        }
        c.setCashIn(r2(cashIn));
        c.setCashOut(r2(cashOut));
        c.setCashExpenses(r2(cashExp));
        c.setExpectedCash(r2(c.getOpeningCash().add(cashIn).subtract(cashOut).subtract(cashExp)));
        c.setUpiTotal(r2(upi));
        c.setCardTotal(r2(card));
        c.setBankTotal(r2(bank));
        c.setSalesTotal(r2(salesTotal));
        c.setCreditSales(r2(credit));
        c.setAdvancesReceived(r2(adv));
        c.setRefundsPaid(r2(refunds));
        return c;
    }

    private static void copy(DayClose from, DayClose to) {
        to.setOpeningCash(from.getOpeningCash());
        to.setCashIn(from.getCashIn());
        to.setCashOut(from.getCashOut());
        to.setCashExpenses(from.getCashExpenses());
        to.setExpectedCash(from.getExpectedCash());
        to.setUpiTotal(from.getUpiTotal());
        to.setCardTotal(from.getCardTotal());
        to.setBankTotal(from.getBankTotal());
        to.setSalesTotal(from.getSalesTotal());
        to.setCreditSales(from.getCreditSales());
        to.setAdvancesReceived(from.getAdvancesReceived());
        to.setRefundsPaid(from.getRefundsPaid());
    }

    static Map<String, Object> closeView(DayClose c) {
        Map<String, Object> m = Views.map();
        m.put("id", c.getId());
        m.put("date", c.getCloseDate());
        m.put("branch", c.getBranch().getName());
        m.put("openingCash", c.getOpeningCash());
        m.put("cashIn", c.getCashIn());
        m.put("cashOut", c.getCashOut());
        m.put("cashExpenses", c.getCashExpenses());
        m.put("expectedCash", c.getExpectedCash());
        m.put("actualCash", c.getId() == null ? null : c.getActualCash());
        m.put("difference", c.getId() == null ? null : c.getDifference());
        m.put("upi", c.getUpiTotal());
        m.put("card", c.getCardTotal());
        m.put("bank", c.getBankTotal());
        m.put("salesTotal", c.getSalesTotal());
        m.put("creditSales", c.getCreditSales());
        m.put("advancesReceived", c.getAdvancesReceived());
        m.put("refundsPaid", c.getRefundsPaid());
        m.put("notes", c.getNotes());
        m.put("closedBy", c.getCreatedBy() == null ? null : c.getCreatedBy().getUsername());
        return m;
    }
}
