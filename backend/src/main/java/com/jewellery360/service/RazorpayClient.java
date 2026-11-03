package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** The few Razorpay calls Jewellery360 needs: UPI QR codes, payment links, their payments, and webhook signatures. */
@Slf4j
@Component
public class RazorpayClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;

    public RazorpayClient(@Value("${app.razorpay.base-url:https://api.razorpay.com}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record Paid(String paymentId, BigDecimal amount, String vpa, String rrn) {}

    /** Single-use UPI QR for exactly this amount. Returns {id, image_url, ...}. */
    public JsonNode createQr(UpiSettingsService.Keys k, String name, BigDecimal amount, String description, long closeByEpoch, Map<String, String> notes) {
        ObjectNode body = json.createObjectNode();
        body.put("type", "upi_qr");
        body.put("name", clip(name, 40));
        body.put("usage", "single_use");
        body.put("fixed_amount", true);
        body.put("payment_amount", paise(amount));
        body.put("description", clip(description, 100));
        body.put("close_by", closeByEpoch);
        body.set("notes", json.valueToTree(notes));
        return call(k, "POST", "/v1/payments/qr_codes", body);
    }

    public void closeQr(UpiSettingsService.Keys k, String qrId) {
        call(k, "POST", "/v1/payments/qr_codes/" + qrId + "/close", null);
    }

    /** Captured payment made on a QR (null while nobody has paid). */
    public Paid qrPayment(UpiSettingsService.Keys k, String qrId) {
        JsonNode res = call(k, "GET", "/v1/payments/qr_codes/" + qrId + "/payments", null);
        for (JsonNode p : res.path("items")) if ("captured".equals(str(p, "status"))) return paid(p);
        return null;
    }

    /** Payment link (customer pays by UPI or any method Razorpay offers). Returns {id, short_url, ...}. */
    public JsonNode createLink(UpiSettingsService.Keys k, BigDecimal amount, String description, String referenceId, String customerName,
                               String customerPhone, long expireByEpoch, Map<String, String> notes) {
        ObjectNode body = json.createObjectNode();
        body.put("amount", paise(amount));
        body.put("currency", "INR");
        body.put("description", clip(description, 2048));
        body.put("reference_id", clip(referenceId, 40));
        body.put("expire_by", expireByEpoch);
        body.put("reminder_enable", false);
        ObjectNode customer = json.createObjectNode();
        if (customerName != null && !customerName.isBlank()) customer.put("name", clip(customerName, 60));
        if (customerPhone != null && !customerPhone.isBlank()) customer.put("contact", customerPhone);
        if (!customer.isEmpty()) body.set("customer", customer);
        ObjectNode notify = body.putObject("notify");
        notify.put("sms", false);
        notify.put("email", false);
        body.set("notes", json.valueToTree(notes));
        return call(k, "POST", "/v1/payment_links", body);
    }

    public JsonNode getLink(UpiSettingsService.Keys k, String linkId) {
        return call(k, "GET", "/v1/payment_links/" + linkId, null);
    }

    public void cancelLink(UpiSettingsService.Keys k, String linkId) {
        call(k, "POST", "/v1/payment_links/" + linkId + "/cancel", null);
    }

    public Paid payment(UpiSettingsService.Keys k, String paymentId) {
        return paid(call(k, "GET", "/v1/payments/" + paymentId, null));
    }

    /** Throws when Razorpay does not accept the keys. */
    public void checkKeys(UpiSettingsService.Keys k) {
        call(k, "GET", "/v1/payments?count=1", null);
    }

    /** Razorpay signs the raw webhook body with the webhook secret (HMAC-SHA256, hex). */
    public static boolean validSignature(String rawBody, String signature, String secret) {
        if (rawBody == null || signature == null || secret == null || secret.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    static Paid paid(JsonNode p) {
        if (p == null || p.isMissingNode() || p.isNull()) return null;
        String rrn = str(p.path("acquirer_data"), "rrn");
        BigDecimal amount = p.hasNonNull("amount") ? BigDecimal.valueOf(p.get("amount").asLong()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP) : null;
        return new Paid(str(p, "id"), amount, str(p, "vpa"), rrn);
    }

    private JsonNode call(UpiSettingsService.Keys k, String method, String path, JsonNode body) {
        if (k == null || k.keyId().isBlank() || k.keySecret().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay is not set up. Add the keys in Settings → UPI payments.");
        String auth = Base64.getEncoder().encodeToString((k.keyId() + ":" + k.keySecret()).getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + auth).header("Content-Type", "application/json");
        b = "GET".equals(method) ? b.GET() : b.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString()));
        HttpResponse<String> res;
        try {
            res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unreachable(method, path, e);
        } catch (Exception e) {
            throw unreachable(method, path, e);
        }
        JsonNode out;
        try { out = json.readTree(res.body() == null || res.body().isBlank() ? "{}" : res.body()); } catch (Exception e) { out = json.createObjectNode(); }
        if (res.statusCode() == 401)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay did not accept the Key ID / Key Secret. Check them in Settings → UPI payments.");
        if (res.statusCode() >= 400) {
            String why = str(out.path("error"), "description");
            log.warn("Razorpay {} {} answered {}: {}", method, path, res.statusCode(), why);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay refused: " + (why == null ? "error " + res.statusCode() : why));
        }
        return out;
    }

    private static ResponseStatusException unreachable(String method, String path, Exception e) {
        log.warn("Razorpay call failed: {} {} - {}", method, path, e.toString());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Razorpay. Check the internet connection and try again.");
    }

    private static long paise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    static String str(JsonNode o, String key) {
        return o != null && o.hasNonNull(key) ? o.get(key).asText() : null;
    }

    private static String clip(String v, int max) {
        if (v == null) return "";
        return v.length() > max ? v.substring(0, max) : v;
    }
}
