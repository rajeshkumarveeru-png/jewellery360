package com.jewellery360.service;

import com.jewellery360.domain.*;
import com.jewellery360.repository.CustomerAdvanceRepository;
import com.jewellery360.repository.PaymentRepository;
import com.jewellery360.repository.SaleRepository;
import com.jewellery360.support.Errors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

import static com.jewellery360.support.Num.*;

/**
 * Low-level money movements shared by billing, advances, repairs, purchases and karigar payments.
 * Every method must run inside the caller's transaction so a bill and its payments are saved together or not at all.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentService {
    public static final List<String> MONEY_MODES = List.of("CASH", "UPI", "CARD", "BANK", "CHEQUE");

    private final PaymentRepository payments;
    private final CustomerAdvanceRepository advances;
    private final SaleRepository sales;
    private final NumberingService numbering;
    private final TenantContext tenant;

    public static String moneyMode(String mode) {
        String m = mode == null ? "CASH" : mode.trim().toUpperCase(Locale.ROOT);
        if (!MONEY_MODES.contains(m)) throw Errors.bad("Payment mode must be one of " + String.join(", ", MONEY_MODES) + ".");
        return m;
    }

    /** Creates one payment row with the next receipt / refund / payout number. */
    public Payment record(TenantContext.Ctx ctx, String type, String mode, BigDecimal amount, Consumer<Payment> links, String reference, String notes) {
        if (amount == null || amount.signum() <= 0) throw Errors.bad("Amount must be more than 0.");
        Payment p = new Payment();
        p.setCompany(ctx.company());
        p.setBranch(ctx.branch());
        p.setPaymentType(type);
        p.setPaymentMode(mode);
        p.setAmount(r2(amount));
        p.setPaymentDate(GoldRateService.today());
        String prefix = switch (type) { case Payment.REFUND -> "REF"; case Payment.PAYOUT -> "PAY"; default -> "RCPT"; };
        p.setPaymentNo(numbering.next(ctx.company(), prefix, p.getPaymentDate()));
        p.setReference(reference);
        p.setNotes(notes);
        p.setCreatedBy(tenant.user(ctx.me()));
        if (links != null) links.accept(p);
        return payments.save(p);
    }

    /** Applies a receipt (or reversal) to the bill's paid amount and payment status. */
    public void applyToSale(Sale sale, BigDecimal delta) {
        sale.setPaidAmount(r2(sale.getPaidAmount().add(delta)));
        BigDecimal due = sale.getTotal().subtract(sale.getReturnedAmount());
        sale.setPaymentStatus(sale.getPaidAmount().signum() <= 0 ? "UNPAID" : sale.getPaidAmount().compareTo(due) >= 0 ? "PAID" : "PARTIAL");
    }

    /**
     * Uses the customer's open advances (advances for the given order first, then oldest first) for a bill.
     * Creates one RECEIPT row of mode ADVANCE per advance used.
     */
    public List<Payment> useAdvances(TenantContext.Ctx ctx, Customer customer, BigDecimal amount, CustomOrder preferred, Consumer<Payment> links) {
        BigDecimal left = r2(amount);
        List<CustomerAdvance> open = new ArrayList<>(advances.findByCustomerIdOrderByAdvanceDateAscIdAsc(customer.getId()).stream()
                .filter(a -> "OPEN".equals(a.getStatus())).toList());
        if (preferred != null) open.sort(Comparator.comparing(a -> a.getCustomOrder() != null && a.getCustomOrder().getId().equals(preferred.getId()) ? 0 : 1));
        BigDecimal available = open.stream().map(CustomerAdvance::getBalanceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(left) < 0) throw Errors.bad("The customer has only ₹" + r2(available).toPlainString() + " advance available.");
        List<Payment> out = new ArrayList<>();
        for (CustomerAdvance a0 : open) {
            if (left.signum() <= 0) break;
            CustomerAdvance a = advances.lock(a0.getId()).orElseThrow();
            BigDecimal use = a.getBalanceAmount().min(left);
            if (use.signum() <= 0) continue;
            a.setUsedAmount(a.getUsedAmount().add(use));
            a.recalc();
            left = left.subtract(use);
            out.add(record(ctx, Payment.RECEIPT, Payment.MODE_ADVANCE, use, p -> { p.setCustomer(customer); p.setAdvance(a); if (links != null) links.accept(p); },
                    a.getAdvanceNo(), "Advance adjusted"));
        }
        return out;
    }

    /** New advance held for the customer. A RECEIPT-source advance also records the money received (type ADVANCE). */
    public CustomerAdvance createAdvance(TenantContext.Ctx ctx, Customer customer, BigDecimal amount, String mode, String source,
                                         CustomOrder order, String reference, String notes) {
        if (amount == null || amount.signum() <= 0) throw Errors.bad("Advance amount must be more than 0.");
        CustomerAdvance a = new CustomerAdvance();
        a.setCompany(ctx.company());
        a.setBranch(ctx.branch());
        a.setCustomer(customer);
        a.setAdvanceDate(GoldRateService.today());
        a.setAdvanceNo(numbering.next(ctx.company(), "ADV", a.getAdvanceDate()));
        a.setAmount(r2(amount));
        a.setSource(source);
        a.setCustomOrder(order);
        a.setNotes(notes);
        a.setCreatedBy(tenant.user(ctx.me()));
        a.recalc();
        advances.save(a);
        if ("RECEIPT".equals(source)) {
            record(ctx, Payment.ADVANCE, moneyMode(mode), amount, p -> { p.setCustomer(customer); p.setAdvance(a); }, reference,
                    order == null ? "Advance received" : "Advance for order " + order.getOrderNo());
        }
        return a;
    }

    /** Pays (part of) an advance back to the customer. */
    public Payment refundAdvance(TenantContext.Ctx ctx, CustomerAdvance a0, BigDecimal amount, String mode, String reference) {
        CustomerAdvance a = advances.lock(a0.getId()).orElseThrow();
        if (amount == null || amount.signum() <= 0) throw Errors.bad("Refund amount must be more than 0.");
        if (amount.compareTo(a.getBalanceAmount()) > 0) throw Errors.bad("Only ₹" + r2(a.getBalanceAmount()).toPlainString() + " of this advance is left.");
        a.setRefundedAmount(a.getRefundedAmount().add(r2(amount)));
        a.recalc();
        return record(ctx, Payment.REFUND, moneyMode(mode), amount, p -> { p.setCustomer(a.getCustomer()); p.setAdvance(a); }, reference, "Advance refunded");
    }

    /**
     * Money the shop owes a customer on a bill (cancellation, return) is either paid out (CASH, UPI ...) or kept as an
     * advance for the next purchase (mode ADVANCE).
     */
    public void refundOnSale(TenantContext.Ctx ctx, Sale sale, BigDecimal amount, String mode, String reason) {
        if (amount.signum() <= 0) return;
        String m = mode == null ? "CASH" : mode.trim().toUpperCase(Locale.ROOT);
        if (Payment.MODE_ADVANCE.equals(m)) {
            CustomerAdvance credit = createAdvance(ctx, sale.getCustomer(), amount, null, "RETURN", null, sale.getInvoiceNo(), reason);
            record(ctx, Payment.REFUND, Payment.MODE_ADVANCE, amount, p -> { p.setCustomer(sale.getCustomer()); p.setSale(sale); p.setAdvance(credit); },
                    credit.getAdvanceNo(), reason + " - kept as advance");
        } else {
            record(ctx, Payment.REFUND, moneyMode(m), amount, p -> { p.setCustomer(sale.getCustomer()); p.setSale(sale); }, null, reason);
        }
        applyToSale(sale, amount.negate());
    }

    /**
     * Customer pays towards dues without naming a bill: oldest unpaid bills first. Anything left over becomes an advance
     * (when allowed) - never silently lost.
     */
    public Map<String, Object> collect(TenantContext.Ctx ctx, Customer customer, BigDecimal amount, String mode, String reference, boolean excessToAdvance) {
        String m = moneyMode(mode);
        BigDecimal left = r2(amount);
        if (left.signum() <= 0) throw Errors.bad("Amount must be more than 0.");
        List<Map<String, Object>> applied = new ArrayList<>();
        for (Sale s0 : sales.unpaidForCustomer(customer.getId())) {
            if (left.signum() <= 0) break;
            Sale s = sales.lock(s0.getId()).orElseThrow();
            BigDecimal due = s.balance();
            if (due.signum() <= 0) continue;
            BigDecimal pay = due.min(left);
            record(ctx, Payment.RECEIPT, m, pay, p -> { p.setCustomer(customer); p.setSale(s); }, reference, "Payment towards " + s.getInvoiceNo());
            applyToSale(s, pay);
            left = left.subtract(pay);
            applied.add(Map.of("saleId", s.getId(), "invoiceNo", s.getInvoiceNo(), "amount", pay));
        }
        CustomerAdvance advance = null;
        if (left.signum() > 0) {
            if (!excessToAdvance) throw Errors.bad("The amount is ₹" + r2(left).toPlainString() + " more than the customer owes. Keep the extra as an advance or enter a smaller amount.");
            advance = createAdvance(ctx, customer, left, m, "RECEIPT", null, reference, "Extra payment kept as advance");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applied", applied);
        out.put("advanceNo", advance == null ? null : advance.getAdvanceNo());
        out.put("advanceAmount", advance == null ? BigDecimal.ZERO : advance.getAmount());
        return out;
    }
}
