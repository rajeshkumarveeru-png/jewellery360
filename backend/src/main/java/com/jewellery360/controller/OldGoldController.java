package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.OldGoldService;
import com.jewellery360.service.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/old-gold")
@RequiredArgsConstructor
public class OldGoldController {
    private final OldGoldService oldGold;
    private final TenantContext tenant;

    @GetMapping public List<Map<String, Object>> list(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) LocalDate from,
                                                      @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "false") boolean openOnly) {
        return oldGold.list(tenant.of(me), from, to, openOnly);
    }
    @GetMapping("/{id}") public Map<String, Object> get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return oldGold.detail(tenant.of(me), id); }
    @PostMapping("/preview") public Map<String, Object> preview(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return oldGold.preview(tenant.withBranch(me), body); }
    @PostMapping public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return oldGold.create(tenant.withBranch(me), body); }
    @PostMapping("/{id}/settle") public Map<String, Object> settle(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) { return oldGold.settle(tenant.withBranch(me), id, body); }
}
