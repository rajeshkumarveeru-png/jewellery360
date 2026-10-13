package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.SalesService;
import com.jewellery360.service.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Bills: preview, create (draft or complete), payments, cancel, return. */
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {
    private final SalesService sales;
    private final TenantContext tenant;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String q, @RequestParam(defaultValue = "300") int limit) {
        return sales.list(tenant.of(me), from, to, status, q, limit);
    }

    @GetMapping("/invoice-number")
    public Map<String, Object> nextInvoiceNo(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) LocalDate date) {
        return sales.nextInvoiceNo(tenant.of(me), date);
    }

    @GetMapping("/invoice-number/check")
    public Map<String, Object> checkInvoiceNo(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam String no) {
        return sales.checkInvoiceNo(tenant.of(me), no);
    }

    @GetMapping("/{id}")public Map<String, Object> get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return sales.detail(tenant.of(me), id); }
    @PostMapping("/preview") public Map<String, Object> preview(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return sales.preview(tenant.withBranch(me), body); }
    @PostMapping public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return sales.create(tenant.withBranch(me), body); }
    @DeleteMapping("/{id}") public Map<String, Object> deleteDraft(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { sales.deleteDraft(tenant.of(me), id); return Map.of("deleted", true); }
    @PostMapping("/{id}/payments") public Map<String, Object> pay(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) { return sales.addPayments(tenant.withBranch(me), id, body); }
    @PostMapping("/{id}/cancel") public Map<String, Object> cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) { return sales.cancel(tenant.withBranch(me), id, body); }
    @PostMapping("/{id}/returns") public Map<String, Object> ret(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) { return sales.returnItems(tenant.withBranch(me), id, body); }
}
