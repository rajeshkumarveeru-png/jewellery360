package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.domain.Company;
import com.jewellery360.domain.Property;
import com.jewellery360.repository.PropertyRepository;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Num;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * UPI settings per company (stored as company properties):
 * - the shop's own UPI ID: a QR for the exact amount, the cashier confirms the money arrived;
 * - Razorpay: QR / payment link, confirmed by Razorpay (webhook or status check).
 * Secrets are write-only: they are saved but never sent back to the browser.
 */
@Service
@RequiredArgsConstructor
public class UpiSettingsService {
    static final String QR_ENABLED = "UPI_QR_ENABLED";
    static final String VPA = "UPI_VPA";
    static final String PAYEE = "UPI_PAYEE_NAME";
    static final String RZP_ENABLED = "UPI_RAZORPAY_ENABLED";
    static final String RZP_KEY_ID = "UPI_RAZORPAY_KEY_ID";
    static final String RZP_KEY_SECRET = "UPI_RAZORPAY_KEY_SECRET";
    static final String RZP_WEBHOOK_SECRET = "UPI_RAZORPAY_WEBHOOK_SECRET";
    static final String PREFERRED = "UPI_PREFERRED";
    static final String QR_MINUTES = "UPI_QR_MINUTES";

    private static final Pattern VPA_RX = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9.\\-]{1,64}$");
    private static final Pattern KEY_ID_RX = Pattern.compile("^rzp_(test|live)_[A-Za-z0-9]{6,40}$");

    private final PropertyRepository properties;
    private final PermissionService permissions;
    private final AuditService audit;

    /** What the app needs to know (no secrets). */
    public record Settings(boolean qrEnabled, String vpa, String payeeName,
                           boolean razorpayEnabled, String razorpayKeyId, boolean razorpaySecretConfigured,
                           boolean webhookSecretConfigured, String preferred, int qrMinutes) {
        public boolean qrReady() { return qrEnabled && !vpa.isBlank(); }
        public boolean razorpayReady() { return razorpayEnabled && !razorpayKeyId.isBlank() && razorpaySecretConfigured; }
        public String razorpayMode() { return razorpayKeyId.isBlank() ? null : razorpayKeyId.startsWith("rzp_live_") ? "LIVE" : "TEST"; }
    }

    /** Keys used to call Razorpay (server only). */
    public record Keys(String keyId, String keySecret, String webhookSecret) {}

    @Transactional(readOnly = true)
    public Settings settings(Long companyId) {
        int minutes;
        try { minutes = Integer.parseInt(value(companyId, QR_MINUTES, "15")); } catch (NumberFormatException e) { minutes = 15; }
        return new Settings(
                Boolean.parseBoolean(value(companyId, QR_ENABLED, "false")),
                value(companyId, VPA, ""),
                value(companyId, PAYEE, ""),
                Boolean.parseBoolean(value(companyId, RZP_ENABLED, "false")),
                value(companyId, RZP_KEY_ID, ""),
                !value(companyId, RZP_KEY_SECRET, "").isBlank(),
                !value(companyId, RZP_WEBHOOK_SECRET, "").isBlank(),
                value(companyId, PREFERRED, "RAZORPAY"),
                Math.max(2, Math.min(120, minutes)));
    }

    @Transactional(readOnly = true)
    public Keys keys(Long companyId) {
        return new Keys(value(companyId, RZP_KEY_ID, ""), value(companyId, RZP_KEY_SECRET, ""), value(companyId, RZP_WEBHOOK_SECRET, ""));
    }

    /** Everyone who bills may read the settings (they hold no secrets). */
    @Transactional(readOnly = true)
    public Map<String, Object> read(TenantContext.Ctx ctx) {
        return view(settings(ctx.companyId()));
    }

    /** Body: {qrEnabled, vpa, payeeName, razorpayEnabled, razorpayKeyId, razorpayKeySecret?, razorpayWebhookSecret?, preferred, qrMinutes}. */
    @Transactional
    public Map<String, Object> update(TenantContext.Ctx ctx, JsonNode u) {
        permissions.requirePower(ctx.me(), "EDIT_SETTINGS");
        Company company = ctx.company();
        Long id = company.getId();
        String vpa = trim(Num.text(u, "vpa"));
        String payee = trim(Num.text(u, "payeeName"));
        String keyId = trim(Num.text(u, "razorpayKeyId"));
        boolean qrOn = Num.bool(u, "qrEnabled", false);
        boolean rzpOn = Num.bool(u, "razorpayEnabled", false);

        if (!vpa.isEmpty() && !VPA_RX.matcher(vpa).matches()) throw Errors.bad("The UPI ID looks wrong. It is like shopname@okaxis or 9876543210@ybl.");
        if (qrOn && vpa.isEmpty()) throw Errors.bad("Enter the shop's UPI ID to turn on the UPI QR.");
        if (payee.length() > 60) throw Errors.bad("Keep the name shown to customers under 60 characters.");
        if (!keyId.isEmpty() && !KEY_ID_RX.matcher(keyId).matches()) throw Errors.bad("The Razorpay Key ID starts with rzp_test_ or rzp_live_.");
        boolean secretSaved = !value(id, RZP_KEY_SECRET, "").isBlank();
        String newSecret = trim(Num.text(u, "razorpayKeySecret"));
        if (rzpOn && (keyId.isEmpty() || (newSecret.isEmpty() && !secretSaved))) throw Errors.bad("Enter the Razorpay Key ID and Key Secret to turn Razorpay on.");

        Map<String, Object> before = view(settings(id));
        save(company, QR_ENABLED, String.valueOf(qrOn));
        save(company, VPA, vpa);
        save(company, PAYEE, payee);
        save(company, RZP_ENABLED, String.valueOf(rzpOn));
        save(company, RZP_KEY_ID, keyId);
        if (!newSecret.isEmpty()) save(company, RZP_KEY_SECRET, newSecret);
        String hook = trim(Num.text(u, "razorpayWebhookSecret"));
        if (!hook.isEmpty()) save(company, RZP_WEBHOOK_SECRET, hook);
        save(company, PREFERRED, "UPI_QR".equalsIgnoreCase(trim(Num.text(u, "preferred"))) ? "UPI_QR" : "RAZORPAY");
        if (u != null && u.hasNonNull("qrMinutes")) save(company, QR_MINUTES, String.valueOf(Math.max(2, Math.min(120, u.get("qrMinutes").asInt(15)))));
        Map<String, Object> after = view(settings(id));
        audit.log(ctx, "UPI_SETTINGS_SAVE", "Company", id, before, after);   // views hold no secrets
        return after;
    }

    public static Map<String, Object> view(Settings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("qrEnabled", s.qrEnabled());
        m.put("qrReady", s.qrReady());
        m.put("vpa", s.vpa());
        m.put("payeeName", s.payeeName());
        m.put("razorpayEnabled", s.razorpayEnabled());
        m.put("razorpayReady", s.razorpayReady());
        m.put("razorpayKeyId", s.razorpayKeyId());
        m.put("razorpayMode", s.razorpayMode());
        m.put("razorpaySecretConfigured", s.razorpaySecretConfigured());
        m.put("webhookSecretConfigured", s.webhookSecretConfigured());
        m.put("preferred", s.preferred());
        m.put("qrMinutes", s.qrMinutes());
        m.put("webhookPath", "/api/webhooks/razorpay");
        return m;
    }

    private String value(Long companyId, String key, String fallback) {
        return properties.findByCompanyIdAndKeyAndActiveTrue(companyId, key).map(Property::getValue).filter(v -> v != null).orElse(fallback);
    }

    private void save(Company company, String key, String value) {
        Property p = properties.findByCompanyIdAndKeyAndActiveTrue(company.getId(), key).orElseGet(Property::new);
        p.setCompany(company);
        p.setKey(key);
        p.setValue(value);
        p.setType("STRING");
        p.setActive(true);
        properties.save(p);
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }
}
