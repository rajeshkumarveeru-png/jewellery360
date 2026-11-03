package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.*;
import com.jewellery360.repository.AppUserRepository;
import com.jewellery360.repository.DayCloseRepository;
import com.jewellery360.repository.SaleRepository;
import com.jewellery360.repository.UpiPaymentRequestRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Num;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Collect a bill's money by UPI.
 * - UPI_QR: a QR for the shop's own UPI ID with the exact amount; the cashier confirms when the money shows up.
 * - RAZORPAY_QR / RAZORPAY_LINK: Razorpay tells us when it is paid (webhook, or we ask Razorpay when the screen checks).
 * Paid money is recorded on the bill as a UPI receipt (same rules as any payment: never more than the balance, never on a closed day).
 * A request is settled once only (row lock), so a webhook and a status check can never record it twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpiPaymentService {
    private static final Pattern UTR_RX = Pattern.compile("^[A-Za-z0-9]{6,30}$");

    private final UpiPaymentRequestRepository requests;
    private final UpiSettingsService settingsService;
    private final RazorpayClient razorpay;
    private final SaleRepository sales;
    private final DayCloseRepository dayCloses;
    private final AppUserRepository users;
    private final PaymentService paymentService;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;
    private final ObjectMapper json = new ObjectMapper();

    /* =================================================================== create ============================== */

    /** Body: {saleId, mode? (UPI_QR | RAZORPAY_QR | RAZORPAY_LINK), amount? (default: the balance)}. */
    @Transactional
    public Map<String, Object> create(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requireWrite(ctx.me(), "BILLING");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Long saleId = Num.requireId(body, "saleId", "Bill");
        Sale sale = sales.lock(saleId).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, sale.getCompany(), sale.getBranch());
        if (Sale.DRAFT.equals(sale.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete the bill first, then collect by UPI.");
        if (!Sale.COMPLETED.equals(sale.getStatus()) && !Sale.PARTIALLY_RETURNED.equals(sale.getStatus())) throw Errors.bad("Money can only be collected on a completed bill.");
        BigDecimal balance = r2(sale.balance());
        if (balance.signum() <= 0) throw Errors.bad("Nothing is due on bill " + sale.getInvoiceNo() + ".");
        BigDecimal asked = Num.decOrNull(body, "amount");
        BigDecimal amount = r2(asked == null ? balance : asked);
        if (amount.signum() <= 0) throw Errors.bad("Enter an amount above ₹0.");
        if (amount.compareTo(balance) > 0) throw Errors.bad("Only ₹" + balance.toPlainString() + " is due on this bill.");
        String closed = closedDay(sale.getBranch());
        if (closed != null) throw Errors.bad(closed);

        Long companyId = sale.getCompany().getId();
        UpiSettingsService.Settings s = settingsService.settings(companyId);
        String mode = chooseMode(Num.text(body, "mode"), s);

        // a fresh request replaces any open one for this bill (only one QR is shown to the customer)
        for (UpiPaymentRequest open : requests.findBySaleIdAndStatus(sale.getId(), UpiPaymentRequest.PENDING)) {
            refresh(open, ctx);   // it may have been paid meanwhile
            if (UpiPaymentRequest.PAID.equals(open.getStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A UPI payment for this bill has just arrived. Refresh the bill.");
            if (UpiPaymentRequest.PENDING.equals(open.getStatus())) cancelAtProvider(open, "Replaced by a new request");
        }

        UpiPaymentRequest r = new UpiPaymentRequest();
        r.setCompany(sale.getCompany());
        r.setBranch(sale.getBranch());
        r.setSale(sale);
        r.setMode(mode);
        r.setAmount(amount);
        r.setCreatedBy(tenant.user(ctx.me()));
        r.setExpiresAt(Instant.now().plus(Duration.ofMinutes(UpiPaymentRequest.RAZORPAY_LINK.equals(mode) ? 24 * 60 : s.qrMinutes())));
        r = requests.save(r);

        String payee = payee(s, sale.getCompany());
        String description = "Bill " + sale.getInvoiceNo();
        Map<String, String> notes = Map.of("j360_request_id", String.valueOf(r.getId()), "invoice_no", sale.getInvoiceNo(), "company_id", String.valueOf(companyId));
        switch (mode) {
            case UpiPaymentRequest.UPI_QR -> r.setUpiUri(upiUri(s.vpa(), payee, amount, description, "J360" + r.getId()));
            case UpiPaymentRequest.RAZORPAY_QR -> {
                JsonNode qr = razorpay.createQr(settingsService.keys(companyId), payee, amount, description, r.getExpiresAt().getEpochSecond(), notes);
                r.setProviderRef(RazorpayClient.str(qr, "id"));
                r.setQrImageUrl(RazorpayClient.str(qr, "image_url"));
            }
            default -> {
                Customer c = sale.getCustomer();
                boolean named = c != null && !CustomerService.isWalkIn(c);
                JsonNode link = razorpay.createLink(settingsService.keys(companyId), amount, description, "J360-" + r.getId() + "-" + System.currentTimeMillis() % 100000,
                        named ? c.getName() : null, named ? phone(c.getPhone()) : null, r.getExpiresAt().getEpochSecond(), notes);
                r.setProviderRef(RazorpayClient.str(link, "id"));
                r.setShortUrl(RazorpayClient.str(link, "short_url"));
            }
        }
        r = requests.save(r);
        audit.log(ctx, "UPI_REQUEST", "Sale", sale.getId(), null, Map.of("invoiceNo", sale.getInvoiceNo(), "mode", mode, "amount", amount, "requestId", r.getId()));
        return view(r, s);
    }

    /* =================================================================== status / list / confirm / cancel ===== */

    /** Asks Razorpay (while pending) what happened - works without a public webhook. */
    @Transactional
    public Map<String, Object> status(TenantContext.Ctx ctx, Long id) {
        UpiPaymentRequest r = mine(ctx, id);
        if (UpiPaymentRequest.PENDING.equals(r.getStatus())) refresh(r, ctx);
        return view(requests.findById(id).orElseThrow(), settingsService.settings(r.getCompany().getId()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forSale(TenantContext.Ctx ctx, Long saleId) {
        permissions.requirePage(ctx.me(), "BILLING");
        Sale sale = sales.findById(saleId).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, sale.getCompany(), sale.getBranch());
        UpiSettingsService.Settings s = settingsService.settings(sale.getCompany().getId());
        return requests.findBySaleIdOrderByIdDesc(saleId).stream().map(r -> view(r, s)).toList();
    }

    /** Shop UPI QR: the cashier saw the money arrive (optionally with the UPI reference / UTR). */
    @Transactional
    public Map<String, Object> confirm(TenantContext.Ctx ctx, Long id, JsonNode body) {
        UpiPaymentRequest r = mine(ctx, id);
        permissions.requireWrite(ctx.me(), "BILLING");
        if (!UpiPaymentRequest.UPI_QR.equals(r.getMode())) throw Errors.bad("Razorpay confirms this payment by itself.");
        if (!UpiPaymentRequest.PENDING.equals(r.getStatus())) throw Errors.bad("This UPI request is already " + r.getStatus().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
        String ref = Optional.ofNullable(Num.text(body, "utr")).orElse("");
        if (!ref.isEmpty() && !UTR_RX.matcher(ref).matches()) throw Errors.bad("The UPI reference (UTR) has 6-30 letters or digits, usually 12 digits.");
        settle(r.getId(), new RazorpayClient.Paid(null, r.getAmount(), null, ref.isEmpty() ? null : ref), ctx);
        return view(requests.findById(id).orElseThrow(), settingsService.settings(r.getCompany().getId()));
    }

    @Transactional
    public Map<String, Object> cancel(TenantContext.Ctx ctx, Long id) {
        UpiPaymentRequest r = mine(ctx, id);
        if (UpiPaymentRequest.PENDING.equals(r.getStatus())) {
            refresh(r, ctx);
            if (UpiPaymentRequest.PENDING.equals(r.getStatus())) cancelAtProvider(r, "Cancelled at the counter");
        }
        return view(requests.findById(id).orElseThrow(), settingsService.settings(r.getCompany().getId()));
    }

    /* =================================================================== Razorpay webhook ==================== */

    /** Returns false when the signature does not match (the caller answers 400). Unknown events are acknowledged. */
    @Transactional
    public boolean webhook(String rawBody, String signature) {
        JsonNode event;
        try { event = json.readTree(rawBody); } catch (Exception e) { return false; }
        String type = Optional.ofNullable(RazorpayClient.str(event, "event")).orElse("");
        JsonNode payload = event.path("payload");
        String ref = null;
        if (type.startsWith("qr_code.")) ref = RazorpayClient.str(payload.path("qr_code").path("entity"), "id");
        else if (type.startsWith("payment_link.")) ref = RazorpayClient.str(payload.path("payment_link").path("entity"), "id");
        if (ref == null) return true;
        Optional<UpiPaymentRequest> found = requests.findFirstByProviderRef(ref);
        if (found.isEmpty()) return true;
        UpiPaymentRequest r = found.get();
        String secret = settingsService.keys(r.getCompany().getId()).webhookSecret();
        if (!RazorpayClient.validSignature(rawBody, signature, secret)) {
            log.warn("Razorpay webhook for {} rejected: signature does not match (webhook secret set: {})", ref, !secret.isBlank());
            return false;
        }
        if (type.endsWith(".credited") || type.endsWith(".paid")) {
            RazorpayClient.Paid paid = RazorpayClient.paid(payload.path("payment").path("entity"));
            if (paid != null && paid.amount() != null) settle(r.getId(), paid, asCreator(r));
        } else if (type.endsWith(".closed") || type.endsWith(".expired") || type.endsWith(".cancelled")) {
            UpiPaymentRequest locked = requests.lock(r.getId()).orElseThrow();
            if (UpiPaymentRequest.PENDING.equals(locked.getStatus())) {
                locked.setStatus(type.endsWith(".cancelled") ? UpiPaymentRequest.CANCELLED : UpiPaymentRequest.EXPIRED);
                requests.save(locked);
            }
        }
        return true;
    }

    /* =================================================================== internals =========================== */

    /** Ask Razorpay (or the clock) what happened to a pending request. */
    private void refresh(UpiPaymentRequest r, TenantContext.Ctx ctx) {
        if (!UpiPaymentRequest.PENDING.equals(r.getStatus())) return;
        Long companyId = r.getCompany().getId();
        try {
            RazorpayClient.Paid paid = null;
            if (UpiPaymentRequest.RAZORPAY_QR.equals(r.getMode()) && r.getProviderRef() != null) {
                paid = razorpay.qrPayment(settingsService.keys(companyId), r.getProviderRef());
            } else if (UpiPaymentRequest.RAZORPAY_LINK.equals(r.getMode()) && r.getProviderRef() != null) {
                JsonNode link = razorpay.getLink(settingsService.keys(companyId), r.getProviderRef());
                String st = RazorpayClient.str(link, "status");
                if ("paid".equals(st)) {
                    for (JsonNode p : link.path("payments")) {
                        String pid = RazorpayClient.str(p, "payment_id");
                        paid = pid != null ? razorpay.payment(settingsService.keys(companyId), pid) : RazorpayClient.paid(p);
                        break;
                    }
                } else if ("expired".equals(st) || "cancelled".equals(st)) {
                    r.setStatus("expired".equals(st) ? UpiPaymentRequest.EXPIRED : UpiPaymentRequest.CANCELLED);
                    requests.save(r);
                    return;
                }
            }
            if (paid != null && paid.amount() != null) { settle(r.getId(), paid, ctx); return; }
        } catch (ResponseStatusException e) {
            log.warn("Could not check UPI request {} with Razorpay: {}", r.getId(), e.getReason());
            return;   // keep it pending; the next check (or the webhook) will pick it up
        }
        if (!UpiPaymentRequest.UPI_QR.equals(r.getMode()) && r.getExpiresAt() != null && Instant.now().isAfter(r.getExpiresAt().plus(Duration.ofMinutes(2)))) {
            r.setStatus(UpiPaymentRequest.EXPIRED);
            requests.save(r);
        }
    }

    /** Records the money on the bill once. Anything that would refuse it turns the request into NEEDS_REVIEW (the money is never lost in a rollback). */
    private void settle(Long requestId, RazorpayClient.Paid paid, TenantContext.Ctx ctx) {
        UpiPaymentRequest r = requests.lock(requestId).orElseThrow();
        if (UpiPaymentRequest.PAID.equals(r.getStatus()) || UpiPaymentRequest.NEEDS_REVIEW.equals(r.getStatus())) return;   // already handled
        Sale sale = sales.lock(r.getSale().getId()).orElseThrow();
        r.setProviderPaymentId(paid.paymentId());
        r.setPayerVpa(paid.vpa());
        r.setUtr(paid.rrn());
        r.setPaidAmount(paid.amount());
        r.setPaidAt(Instant.now());

        BigDecimal balance = r2(sale.balance());
        String problem = null;
        if (paid.amount().compareTo(r2(r.getAmount())) != 0) problem = "Paid ₹" + paid.amount().toPlainString() + " instead of ₹" + r2(r.getAmount()).toPlainString() + ".";
        else if (!Sale.COMPLETED.equals(sale.getStatus()) && !Sale.PARTIALLY_RETURNED.equals(sale.getStatus())) problem = "The bill is " + sale.getStatus().toLowerCase(Locale.ROOT) + ".";
        else if (paid.amount().compareTo(balance) > 0) problem = "The bill now has only ₹" + balance.toPlainString() + " due (paid another way meanwhile?).";
        else problem = closedDay(sale.getBranch());
        if (problem != null) {
            r.setStatus(UpiPaymentRequest.NEEDS_REVIEW);
            r.setNote(clip("UPI money received but not put on the bill: " + problem, 500));
            requests.save(r);
            audit.log(ctx, "UPI_NEEDS_REVIEW", "Sale", sale.getId(), null, Map.of("invoiceNo", sale.getInvoiceNo(), "requestId", r.getId(), "reason", problem));
            log.warn("UPI request {} needs review: {}", r.getId(), problem);
            return;
        }

        boolean ownQr = UpiPaymentRequest.UPI_QR.equals(r.getMode());
        String reference = ownQr ? (paid.rrn() != null ? "UPI " + paid.rrn() : "UPI QR #" + r.getId())
                : "Razorpay " + paid.paymentId() + (paid.rrn() != null ? " / " + paid.rrn() : "");
        String notes = ownQr ? "Shop UPI QR, confirmed at the counter" : "Paid through Razorpay (" + (UpiPaymentRequest.RAZORPAY_QR.equals(r.getMode()) ? "QR" : "payment link") + ")";
        TenantContext.Ctx at = new TenantContext.Ctx(ctx.me(), sale.getCompany(), sale.getBranch());   // the money belongs to the bill's branch
        Payment p = paymentService.record(at, Payment.RECEIPT, "UPI", paid.amount(), x -> { x.setCustomer(sale.getCustomer()); x.setSale(sale); }, clip(reference, 160), notes);
        paymentService.applyToSale(sale, paid.amount());
        r.setPayment(p);
        r.setStatus(UpiPaymentRequest.PAID);
        requests.save(r);
        audit.log(at, "BILL_PAYMENT", "Sale", sale.getId(), null, Map.of("invoiceNo", sale.getInvoiceNo(), "paid", sale.getPaidAmount(), "upi", reference));
    }

    /** Webhooks arrive without a signed-in user: the money is recorded as the person who asked for it. */
    private TenantContext.Ctx asCreator(UpiPaymentRequest r) {
        AppUser u = users.findById(r.getCreatedBy().getId()).orElseThrow();
        AuthenticatedUser me = new AuthenticatedUser(u.getId(), r.getCompany().getId(), r.getBranch().getId(), u.getUsername(), u.getRole().name());
        return new TenantContext.Ctx(me, r.getCompany(), r.getBranch());
    }

    private void cancelAtProvider(UpiPaymentRequest r, String why) {
        try {
            UpiSettingsService.Keys k = settingsService.keys(r.getCompany().getId());
            if (UpiPaymentRequest.RAZORPAY_QR.equals(r.getMode()) && r.getProviderRef() != null) razorpay.closeQr(k, r.getProviderRef());
            if (UpiPaymentRequest.RAZORPAY_LINK.equals(r.getMode()) && r.getProviderRef() != null) razorpay.cancelLink(k, r.getProviderRef());
        } catch (ResponseStatusException e) {
            log.warn("Could not close UPI request {} at Razorpay: {}", r.getId(), e.getReason());
        }
        r.setStatus(UpiPaymentRequest.CANCELLED);
        r.setNote(why);
        requests.save(r);
    }

    private UpiPaymentRequest mine(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "BILLING");
        UpiPaymentRequest r = requests.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, r.getCompany(), r.getBranch());
        return r;
    }

    /** Money received today can only go on the books while today is open. */
    private String closedDay(Branch branch) {
        return dayCloses.findByBranchIdAndCloseDate(branch.getId(), GoldRateService.today()).isPresent()
                ? "Today is already closed for " + branch.getName() + ". Reopen the day (close it again later) before collecting." : null;
    }

    private static String chooseMode(String asked, UpiSettingsService.Settings s) {
        if (asked != null && !asked.isBlank()) {
            String m = asked.trim().toUpperCase(Locale.ROOT);
            if (!List.of(UpiPaymentRequest.UPI_QR, UpiPaymentRequest.RAZORPAY_QR, UpiPaymentRequest.RAZORPAY_LINK).contains(m)) throw Errors.bad("Unknown UPI mode " + asked + ".");
            if (UpiPaymentRequest.UPI_QR.equals(m) && !s.qrReady()) throw Errors.bad("The shop UPI QR is off. Turn it on in Settings → UPI payments.");
            if (!UpiPaymentRequest.UPI_QR.equals(m) && !s.razorpayReady()) throw Errors.bad("Razorpay is off. Turn it on in Settings → UPI payments.");
            return m;
        }
        if (s.razorpayReady() && (!"UPI_QR".equals(s.preferred()) || !s.qrReady())) return UpiPaymentRequest.RAZORPAY_QR;
        if (s.qrReady()) return UpiPaymentRequest.UPI_QR;
        throw Errors.bad("UPI collection is not set up. Add the shop's UPI ID or Razorpay keys in Settings → UPI payments.");
    }

    static String upiUri(String vpa, String payee, BigDecimal amount, String note, String ref) {
        return "upi://pay?pa=" + enc(vpa) + "&pn=" + enc(payee) + "&am=" + r2(amount).toPlainString() + "&cu=INR&tn=" + enc(note) + "&tr=" + enc(ref);
    }

    private Map<String, Object> view(UpiPaymentRequest r, UpiSettingsService.Settings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("saleId", r.getSale().getId());
        m.put("invoiceNo", r.getSale().getInvoiceNo());
        m.put("mode", r.getMode());
        m.put("amount", r2(r.getAmount()));
        m.put("status", r.getStatus());
        m.put("upiUri", r.getUpiUri());
        m.put("qrImageUrl", r.getQrImageUrl());
        m.put("shortUrl", r.getShortUrl());
        m.put("payeeName", payee(s, r.getCompany()));
        m.put("vpa", UpiPaymentRequest.UPI_QR.equals(r.getMode()) ? s.vpa() : null);
        m.put("expiresAt", r.getExpiresAt());
        m.put("paidAt", r.getPaidAt());
        m.put("paidAmount", r.getPaidAmount() == null ? null : r2(r.getPaidAmount()));
        m.put("utr", r.getUtr());
        m.put("payerVpa", r.getPayerVpa());
        m.put("providerPaymentId", r.getProviderPaymentId());
        m.put("note", r.getNote());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private static String payee(UpiSettingsService.Settings s, Company c) { return s.payeeName().isBlank() ? c.getName() : s.payeeName(); }

    private static String phone(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 10) return "+91" + d;
        if (d.length() == 12 && d.startsWith("91")) return "+" + d;
        return null;
    }

    private static BigDecimal r2(BigDecimal v) { return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP); }
    private static String enc(String v) { return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String clip(String v, int max) { return v == null ? null : v.length() > max ? v.substring(0, max) : v; }
}
