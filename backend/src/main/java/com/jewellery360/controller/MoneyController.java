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
@RequestMapping("/api/money")
@RequiredArgsConstructor
public class MoneyController {
    private final MoneyService money;
    private final TenantContext tenant;

    @GetMapping("/payments") public Map<String, Object> payments(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type, @RequestParam(required = false) String mode) { return money.payments(tenant.of(me), from, to, type, mode); }
    @GetMapping("/advances") public List<Map<String, Object>> advances(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(defaultValue = "true") boolean openOnly) { return money.advances(tenant.of(me), openOnly); }
    @PostMapping("/advances") public Map<String, Object> createAdvance(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return money.createAdvance(tenant.withBranch(me), b); }
    @PostMapping("/advances/{id}/refund") public Map<String, Object> refund(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return money.refundAdvance(tenant.withBranch(me), id, b); }
    @GetMapping("/expenses") public Map<String, Object> expenses(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return money.expenses(tenant.of(me), from, to); }
    @PostMapping("/expenses") public Map<String, Object> createExpense(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return money.createExpense(tenant.withBranch(me), b); }
    @DeleteMapping("/expenses/{id}") public void deleteExpense(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { money.deleteExpense(tenant.of(me), id); }
    @GetMapping("/day-close") public Map<String, Object> dayClose(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) { return money.dayClosePreview(tenant.withBranch(me), date); }
    @PostMapping("/day-close") public Map<String, Object> closeDay(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return money.closeDay(tenant.withBranch(me), b); }
    @GetMapping("/day-close/history") public List<Map<String, Object>> history(@AuthenticationPrincipal AuthenticatedUser me) { return money.dayCloseHistory(tenant.withBranch(me)); }
}
