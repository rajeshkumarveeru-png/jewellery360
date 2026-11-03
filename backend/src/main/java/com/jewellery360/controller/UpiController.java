package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import com.jewellery360.service.RazorpayClient;
import com.jewellery360.service.TenantContext;
import com.jewellery360.service.UpiPaymentService;
import com.jewellery360.service.UpiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** UPI collection: settings (shop UPI QR / Razorpay), payment requests for bills, and the Razorpay webhook. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UpiController {
    private final UpiSettingsService settings;
    private final UpiPaymentService upi;
    private final RazorpayClient razorpay;
    private final PermissionService permissions;
    private final TenantContext tenant;

    /* settings: everyone signed in reads them (no secrets inside); EDIT_SETTINGS changes them */

    @GetMapping("/upi/settings")
    public Map<String, Object> settings(@AuthenticationPrincipal AuthenticatedUser me) { return settings.read(tenant.of(me)); }

    @PutMapping("/upi/settings")
    public Map<String, Object> save(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return settings.update(tenant.of(me), body); }

    /** Checks the saved Razorpay keys against Razorpay. */
    @PostMapping("/upi/settings/test")
    public Map<String, Object> test(@AuthenticationPrincipal AuthenticatedUser me) {
        TenantContext.Ctx ctx = tenant.of(me);
        permissions.requirePower(me, "EDIT_SETTINGS");
        razorpay.checkKeys(settings.keys(ctx.companyId()));
        String mode = String.valueOf(settings.settings(ctx.companyId()).razorpayMode());
        return Map.of("ok", true, "mode", mode, "message", "Razorpay accepted the keys (" + mode + " mode).");
    }

    /* collecting */

    @PostMapping("/upi/requests")
    public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return upi.create(tenant.withBranch(me), body); }

    @GetMapping("/upi/requests/{id}")
    public Map<String, Object> status(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return upi.status(tenant.of(me), id); }

    @GetMapping("/upi/requests")
    public List<Map<String, Object>> forSale(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam Long saleId) { return upi.forSale(tenant.of(me), saleId); }

    @PostMapping("/upi/requests/{id}/confirm")
    public Map<String, Object> confirm(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody(required = false) JsonNode body) {
        return upi.confirm(tenant.of(me), id, body);
    }

    @PostMapping("/upi/requests/{id}/cancel")
    public Map<String, Object> cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return upi.cancel(tenant.of(me), id); }

    /* Razorpay -> Jewellery360 (public; trusted only after the signature check) */

    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody String raw, @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        return upi.webhook(raw, signature)
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Signature check failed"));
    }
}
