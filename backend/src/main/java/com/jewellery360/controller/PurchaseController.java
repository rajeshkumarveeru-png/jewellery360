package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PurchaseController {
    private final PurchaseService purchases;
    private final TenantContext tenant;

    @GetMapping("/purchases") public List<Map<String, Object>> list(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return purchases.list(tenant.of(me), from, to); }
    @GetMapping("/purchases/{id}") public Map<String, Object> get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return purchases.detail(tenant.of(me), id); }
    @PostMapping("/purchases") public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return purchases.create(tenant.withBranch(me), b); }
    @PostMapping("/purchases/{id}/payments") public Map<String, Object> pay(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return purchases.addPayment(tenant.withBranch(me), id, b); }
    @PostMapping("/purchases/{id}/returns") public Map<String, Object> ret(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return purchases.returnItems(tenant.withBranch(me), id, b); }
    @GetMapping("/suppliers/{id}/ledger") public Map<String, Object> ledger(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return purchases.supplierLedger(tenant.of(me), id); }
}
