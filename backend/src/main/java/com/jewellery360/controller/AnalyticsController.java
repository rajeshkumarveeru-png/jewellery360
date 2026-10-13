package com.jewellery360.controller;

import com.jewellery360.domain.Sale;
import com.jewellery360.repository.SaleRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import com.jewellery360.service.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only sales analytics for the executive dashboard:
 * today's sales summary, totals for a chosen From/To date range and a daily / weekly / monthly trend series.
 *
 * Scoped through {@link TenantContext} like every other endpoint (company always; branch when one applies).
 * Cancelled invoices are excluded. Needs the Overview page - the same rule as the dashboard.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final long MAX_RANGE_DAYS = 1100;

    private final SaleRepository sales;
    private final TenantContext tenant;
    private final PermissionService permissions;

    @GetMapping("/sales")
    @Transactional(readOnly = true)
    public Map<String, Object> salesAnalytics(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "DAY") String granularity,
            @RequestHeader(value = "X-Company-Id", required = false) String companyHeader) {

        if (me != null && "APP_ADMIN".equals(me.getRole()) && (companyHeader == null || companyHeader.isBlank()))
            return Map.of("platform", true, "message", "Select company and branch context.");
        TenantContext.Ctx ctx = tenant.of(me);
        permissions.requirePage(me, "OVERVIEW");
        Long cid = ctx.companyId();
        Long bid = ctx.branchId();

        String mode = granularity == null ? "DAY" : granularity.trim().toUpperCase(Locale.ROOT);
        if (!"DAY".equals(mode) && !"WEEK".equals(mode) && !"MONTH".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "granularity must be DAY, WEEK or MONTH");
        }

        LocalDate today = LocalDate.now(INDIA);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after To date");
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range is too large (maximum " + MAX_RANGE_DAYS + " days)");
        }

        // one query covers the chart range plus today / yesterday for the summary widget
        LocalDate yesterday = today.minusDays(1);
        LocalDate fetchFrom = start.isBefore(yesterday) ? start : yesterday;
        LocalDate fetchTo = end.isAfter(today) ? end : today;
        List<Sale> valid = new ArrayList<>();
        valid.addAll(sales.completedBetween(cid, bid, fetchFrom, fetchTo));

        Map<LocalDate, Bucket> buckets = new TreeMap<>();
        Bucket totals = new Bucket();
        Bucket todayBucket = new Bucket();
        Bucket yesterdayBucket = new Bucket();
        for (Sale s : valid) {
            LocalDate d = s.getSaleDate();
            if (d.equals(today)) todayBucket.add(s);
            if (d.equals(yesterday)) yesterdayBucket.add(s);
            if (d.isBefore(start) || d.isAfter(end)) continue;
            totals.add(s);
            buckets.computeIfAbsent(bucketStart(d, mode), k -> new Bucket()).add(s);
        }

        // continuous series: empty days / weeks / months are shown as zero instead of being skipped
        List<Map<String, Object>> series = new ArrayList<>();
        LocalDate cursor = bucketStart(start, mode);
        LocalDate last = bucketStart(end, mode);
        int guard = 0;
        while (!cursor.isAfter(last) && guard++ < 1500) {
            Bucket b = buckets.getOrDefault(cursor, new Bucket());
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", cursor.toString());
            point.put("label", label(cursor, mode));
            point.put("sales", b.total);
            point.put("invoices", b.count);
            point.put("tax", b.tax);
            series.add(point);
            cursor = "MONTH".equals(mode) ? cursor.plusMonths(1) : ("WEEK".equals(mode) ? cursor.plusWeeks(1) : cursor.plusDays(1));
        }

        Map<String, Object> todayOut = new LinkedHashMap<>();
        todayOut.put("date", today.toString());
        todayOut.put("sales", todayBucket.total);
        todayOut.put("invoices", todayBucket.count);
        todayOut.put("tax", todayBucket.tax);
        todayOut.put("yesterdaySales", yesterdayBucket.total);
        todayOut.put("changePercent", changePercent(todayBucket.total, yesterdayBucket.total));

        Map<String, Object> totalsOut = new LinkedHashMap<>();
        totalsOut.put("sales", totals.total);
        totalsOut.put("invoices", totals.count);
        totalsOut.put("tax", totals.tax);
        totalsOut.put("average", totals.count == 0 ? BigDecimal.ZERO : totals.total.divide(BigDecimal.valueOf(totals.count), 2, RoundingMode.HALF_UP));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platform", false);
        out.put("granularity", mode);
        out.put("from", start.toString());
        out.put("to", end.toString());
        out.put("today", todayOut);
        out.put("totals", totalsOut);
        out.put("series", series);
        return out;
    }

    private static LocalDate bucketStart(LocalDate d, String mode) {
        if ("MONTH".equals(mode)) return d.withDayOfMonth(1);
        if ("WEEK".equals(mode)) return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return d;
    }

    private static String label(LocalDate d, String mode) {
        if ("MONTH".equals(mode)) return d.getMonth().toString().substring(0, 1) + d.getMonth().toString().substring(1, 3).toLowerCase(Locale.ROOT) + " " + d.getYear();
        return d.getDayOfMonth() + " " + d.getMonth().toString().substring(0, 1) + d.getMonth().toString().substring(1, 3).toLowerCase(Locale.ROOT);
    }

    private static BigDecimal changePercent(BigDecimal now, BigDecimal before) {
        if (before == null || before.signum() == 0) return now != null && now.signum() > 0 ? new BigDecimal("100.0") : BigDecimal.ZERO;
        return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
    }

    private static final class Bucket {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        long count = 0;

        void add(Sale s) {
            total = total.add(s.getTotal().subtract(s.getReturnedAmount()));
            tax = tax.add(s.getGst() == null ? BigDecimal.ZERO : s.getGst());
            count++;
        }
    }
}
