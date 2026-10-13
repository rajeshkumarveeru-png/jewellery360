package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.GoldRateService;
import com.jewellery360.service.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gold-rates")
@RequiredArgsConstructor
public class GoldRateController {
    private final GoldRateService rates;
    private final TenantContext tenant;

    /** Today's board - every signed-in user may read it (it is shown in the header and on bills). */
    @GetMapping public Map<String, Object> board(@AuthenticationPrincipal AuthenticatedUser me) { return rates.board(tenant.of(me)); }
    @PostMapping public Map<String, Object> save(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return rates.save(tenant.withBranch(me), body); }
    @GetMapping("/history") public List<Map<String, Object>> history(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(defaultValue = "30") int days) { return rates.history(tenant.of(me), days); }
    @GetMapping("/market") public Map<String, Object> market(@AuthenticationPrincipal AuthenticatedUser me) { return rates.marketReference(tenant.of(me)); }
    @GetMapping("/impact") public Map<String, Object> impact(@AuthenticationPrincipal AuthenticatedUser me) { return rates.impact(tenant.of(me)); }
}
