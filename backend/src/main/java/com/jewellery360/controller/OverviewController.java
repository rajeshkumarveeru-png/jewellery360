package com.jewellery360.controller;

import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Owner dashboard, global search and reports. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OverviewController {
    private final DashboardService dashboard;
    private final SearchService search;
    private final ReportService reports;
    private final TenantContext tenant;

    @GetMapping("/dashboard") public Map<String, Object> dashboard(@AuthenticationPrincipal AuthenticatedUser me) { return dashboard.overview(tenant.of(me)); }
    @GetMapping("/search") public Map<String, Object> search(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(defaultValue = "") String q) { return search.search(tenant.of(me), q); }
    @GetMapping("/reports") public List<Map<String, Object>> reports(@AuthenticationPrincipal AuthenticatedUser me) { return reports.catalogue(tenant.of(me)); }
    @GetMapping("/reports/{key}") public Map<String, Object> report(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String key,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return reports.run(tenant.of(me), key, from, to); }
}
