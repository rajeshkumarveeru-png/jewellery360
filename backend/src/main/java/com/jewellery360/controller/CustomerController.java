package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.CustomerService;
import com.jewellery360.service.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customers;
    private final TenantContext tenant;

    @GetMapping public List<Map<String, Object>> list(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String q, @RequestParam(defaultValue = "300") int limit) { return customers.search(tenant.of(me), q, limit); }
    @GetMapping("/{id}") public Map<String, Object> profile(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return customers.profile(tenant.of(me), id); }
    @PostMapping public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return customers.create(tenant.withBranch(me), body); }
    @PutMapping("/{id}") public Map<String, Object> update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) { return customers.update(tenant.of(me), id, body); }
    @DeleteMapping("/{id}") public Map<String, Object> remove(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { customers.deactivate(tenant.of(me), id); return Map.of("removed", true); }
    @PostMapping("/{id}/collect") public Map<String, Object> collect(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) { return customers.collect(tenant.withBranch(me), id, body); }
}
