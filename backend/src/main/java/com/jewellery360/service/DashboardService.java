package com.jewellery360.service;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.support.Views;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.jewellery360.support.Num.*;

/**
 * Owner's overview: today's numbers, money due both ways, stock by purity, pending work and a short list of insights.
 * Every insight is computed from the shop's own records and only shown when there is something to say.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final PaymentRepository payments;
    private final CustomerAdvanceRepository advances;
    private final PurchaseOrderRepository purchases;
    private final JewelleryItemRepository items;
    private final CustomOrderRepository orders;
    private final RepairOrderRepository repairs;
    private final CustomerRepository customers;
    private final DayCloseRepository dayCloses;
    private final PurityMasterRepository purities;
    private final GoldRateService rates;
    private final PermissionService permissions;

    @Transactional(readOnly = true)
    public Map<String, Object> overview(TenantContext.Ctx ctx) {
        permissions.requirePage(ctx.me(), "OVERVIEW");
        Long c = ctx.companyId(), b = ctx.branchId();
        LocalDate today = GoldRateService.today();
        Map<String, Object> out = Views.map();
        out.put("date", today);
        out.put("branch", ctx.branch() == null ? "All branches" : ctx.branch().getName());

        /* today */
        List<Sale> todaySales = sales.completedBetween(c, b, today, today);
        Map<String, Object> t = Views.map();
        t.put("bills", todaySales.size());
        t.put("sales", r2(todaySales.stream().map(s -> s.getTotal().subtract(s.getReturnedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add)));
        t.put("goldSoldGrams", r3(saleItems.soldBetween(c, b, today, today).stream().map(SaleItem::getNetWeight).reduce(BigDecimal.ZERO, BigDecimal::add)));
        BigDecimal in = BigDecimal.ZERO, outMoney = BigDecimal.ZERO;
        Map<String, BigDecimal> byMode = new TreeMap<>();
        for (Payment p : payments.between(c, b, today, today)) {
            if (!p.isMoney()) continue;
            if (p.direction() > 0) in = in.add(p.getAmount()); else outMoney = outMoney.add(p.getAmount());
            byMode.merge(p.getPaymentMode(), p.getAmount().multiply(BigDecimal.valueOf(p.direction())), BigDecimal::add);
        }
        t.put("moneyIn", r2(in));
        t.put("moneyOut", r2(outMoney));
        t.put("byMode", byMode);
        out.put("today", t);

        /* dues */
        Map<String, Object> d = Views.map();
        d.put("customersOwe", r2(sales.outstanding(c, b)));
        d.put("advancesHeld", r2(advances.openBalance(c, b)));
        if (ctx.admin() || permissions.can(ctx.me(), "PURCHASES")) d.put("weOweSuppliers", r2(purchases.outstanding(c)));
        out.put("dues", d);

        /* stock */
        List<Map<String, Object>> stock = new ArrayList<>();
        for (Object[] r : items.stockByPurity(c, b)) {
            Map<String, Object> m = Views.map();
            m.put("purity", r[0]);
            m.put("metal", r[1]);
            m.put("pieces", ((Number) r[3]).longValue());
            m.put("grossWeight", r3((BigDecimal) r[4]));
            m.put("netWeight", r3((BigDecimal) r[5]));
            m.put("fineWeight", r3(((BigDecimal) r[5]).multiply((BigDecimal) r[2])));
            stock.add(m);
        }
        out.put("stock", stock);

        /* rates */
        List<Map<String, Object>> rateRows = new ArrayList<>();
        boolean ratesSetToday = true;
        if (b != null) {
            Map<Long, GoldRate> map = rates.rateMap(b, today);
            // Purities that were never priced (e.g. a metal the shop doesn't sell) don't count as "not updated".
            if (map.isEmpty()) ratesSetToday = false;
            for (PurityMaster p : purities.findByCompanyIdOrderByMetalAscFinenessDesc(c)) {
                if (!p.isActive()) continue;
                GoldRate g = map.get(p.getId());
                boolean fresh = g != null && today.equals(g.getRateDate());
                if (g != null && !fresh) ratesSetToday = false;
                Map<String, Object> m = Views.map();
                m.put("purity", p.getName());
                m.put("rate", g == null ? null : g.getRatePerGram());
                m.put("setToday", fresh);
                rateRows.add(m);
            }
        }
        out.put("rates", rateRows);

        /* pending work */
        List<CustomOrder> openOrders = orders.list(c, b, null).stream().filter(o -> List.of("ORDERED", "IN_PROGRESS", "READY").contains(o.getStatus())).toList();
        List<RepairOrder> openRepairs = repairs.list(c, b, null).stream().filter(r -> List.of("RECEIVED", "IN_REPAIR", "READY").contains(r.getStatus())).toList();
        long ordersOverdue = openOrders.stream().filter(o -> o.getDeliveryDate() != null && o.getDeliveryDate().isBefore(today) && !"READY".equals(o.getStatus())).count();
        long repairsOverdue = openRepairs.stream().filter(r -> r.getExpectedDate() != null && r.getExpectedDate().isBefore(today) && !"READY".equals(r.getStatus())).count();
        long readyToDeliver = openOrders.stream().filter(o -> "READY".equals(o.getStatus())).count() + openRepairs.stream().filter(r -> "READY".equals(r.getStatus())).count();
        Map<String, Object> w = Views.map();
        w.put("orders", openOrders.size());
        w.put("repairs", openRepairs.size());
        w.put("overdue", ordersOverdue + repairsOverdue);
        w.put("readyToDeliver", readyToDeliver);
        out.put("work", w);

        /* trend: last 14 days */
        LocalDate start = today.minusDays(13);
        TreeMap<LocalDate, BigDecimal> trend = new TreeMap<>();
        for (LocalDate x = start; !x.isAfter(today); x = x.plusDays(1)) trend.put(x, BigDecimal.ZERO);
        for (Sale s : sales.completedBetween(c, b, start, today)) trend.merge(s.getSaleDate(), s.getTotal().subtract(s.getReturnedAmount()), BigDecimal::add);
        out.put("trend", trend.entrySet().stream().map(e -> Map.<String, Object>of("date", e.getKey(), "sales", r2(e.getValue()))).toList());

        /* celebrations */
        List<Map<String, Object>> celebrating = new ArrayList<>();
        for (Customer cu : customers.celebrating(c, today.getMonthValue(), today.getDayOfMonth())) {
            Map<String, Object> m = Views.customerBrief(cu);
            boolean bday = cu.getBirthday() != null && cu.getBirthday().getMonth() == today.getMonth() && cu.getBirthday().getDayOfMonth() == today.getDayOfMonth();
            m.put("occasion", bday ? "Birthday" : "Anniversary");
            celebrating.add(m);
        }
        out.put("celebrating", celebrating);

        /* recent bills */
        out.put("recentBills", sales.list(c, b, today.minusDays(30), today, null, null, PageRequest.of(0, 6)).stream().map(Views::saleSummary).toList());

        out.put("insights", insights(ctx, today, ratesSetToday, ordersOverdue, repairsOverdue, readyToDeliver, celebrating.size()));
        return out;
    }

    private List<Map<String, Object>> insights(TenantContext.Ctx ctx, LocalDate today, boolean ratesSetToday, long ordersOverdue, long repairsOverdue,
                                               long readyToDeliver, int celebrating) {
        Long c = ctx.companyId(), b = ctx.branchId();
        List<Map<String, Object>> out = new ArrayList<>();
        if (b != null && !ratesSetToday && permissions.can(ctx.me(), "GOLD_RATES"))
            out.add(insight("warning", "Today's gold rates are not saved yet. Bills will use the last saved rate until you update them.", "GOLD_RATES"));

        if (b != null && permissions.can(ctx.me(), "MONEY")) {
            LocalDate y = today.minusDays(1);
            boolean hadActivity = !payments.between(c, b, y, y).isEmpty();
            if (hadActivity && dayCloses.findByBranchIdAndCloseDate(b, y).isEmpty())
                out.add(insight("warning", "Yesterday's cash was not closed. Count the drawer and close the day.", "MONEY"));
        }

        List<Sale> unpaid = sales.unpaid(c, b);
        List<Sale> old = unpaid.stream().filter(s -> ChronoUnit.DAYS.between(s.getSaleDate(), today) > 30).toList();
        if (!old.isEmpty()) {
            BigDecimal amt = old.stream().map(Sale::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            long people = old.stream().map(s -> s.getCustomer().getId()).distinct().count();
            out.add(insight("warning", people + " customer" + (people == 1 ? "" : "s") + " owe ₹" + money(amt) + " on bills older than 30 days.", "CUSTOMERS"));
        }

        if (ordersOverdue + repairsOverdue > 0)
            out.add(insight("warning", (ordersOverdue > 0 ? ordersOverdue + " order" + (ordersOverdue == 1 ? "" : "s") : "")
                    + (ordersOverdue > 0 && repairsOverdue > 0 ? " and " : "")
                    + (repairsOverdue > 0 ? repairsOverdue + " repair" + (repairsOverdue == 1 ? "" : "s") : "") + " are past their promised date.", "SERVICES"));
        if (readyToDeliver > 0)
            out.add(insight("info", readyToDeliver + " piece" + (readyToDeliver == 1 ? " is" : "s are") + " ready - call the customer" + (readyToDeliver == 1 ? "" : "s") + " for delivery.", "SERVICES"));

        // Month to date vs the same days last month.
        LocalDate mStart = today.withDayOfMonth(1);
        LocalDate pStart = mStart.minusMonths(1);
        LocalDate pEnd = pStart.plusDays(Math.min(today.getDayOfMonth(), pStart.lengthOfMonth()) - 1);
        BigDecimal now = net(sales.completedBetween(c, b, mStart, today)), prev = net(sales.completedBetween(c, b, pStart, pEnd));
        if (prev.signum() > 0 && now.signum() > 0) {
            BigDecimal pct = now.subtract(prev).multiply(HUNDRED).divide(prev, 0, RoundingMode.HALF_UP);
            if (pct.abs().compareTo(BigDecimal.valueOf(5)) >= 0)
                out.add(insight(pct.signum() > 0 ? "good" : "info", "Sales this month are " + (pct.signum() > 0 ? "up " : "down ") + pct.abs() + "% on the same days last month (₹"
                        + money(now) + " vs ₹" + money(prev) + ").", "REPORTS"));
        }

        // Best-selling category in the last 30 days.
        Map<String, BigDecimal> cat = new HashMap<>();
        Map<String, Long> catCount = new HashMap<>();
        for (SaleItem si : saleItems.soldBetween(c, b, today.minusDays(29), today)) {
            String k = si.getJewelleryItem().getProduct().getCategory().getName();
            cat.merge(k, si.getTaxableValue(), BigDecimal::add);
            catCount.merge(k, 1L, Long::sum);
        }
        if (cat.size() > 1) {
            BigDecimal total = cat.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            Map.Entry<String, BigDecimal> top = Collections.max(cat.entrySet(), Map.Entry.comparingByValue());
            if (total.signum() > 0)
                out.add(insight("info", top.getKey() + " is your best seller in the last 30 days: " + catCount.get(top.getKey()) + " pieces, "
                        + top.getValue().multiply(HUNDRED).divide(total, 0, RoundingMode.HALF_UP) + "% of sales value.", "REPORTS"));
        }

        // Slow stock.
        List<JewelleryItem> stock = items.list(c, b, JewelleryItem.IN_STOCK, Pageable.unpaged());
        List<JewelleryItem> dead = stock.stream().filter(i -> Views.ageDays(i.getCreatedAt()) > 180).toList();
        if (!dead.isEmpty()) {
            BigDecimal wt = dead.stream().map(JewelleryItem::getNetWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(insight("info", dead.size() + " piece" + (dead.size() == 1 ? " has" : "s have") + " been in stock for over 6 months (" + r3(wt).toPlainString()
                    + " g). Consider re-display, offers or melting.", "REPORTS"));
        }

        if (celebrating > 0)
            out.add(insight("good", celebrating + " customer" + (celebrating == 1 ? " has a birthday or anniversary" : "s have birthdays or anniversaries") + " today - a good day to send wishes.", "CUSTOMERS"));
        return out;
    }

    private static BigDecimal net(List<Sale> list) { return list.stream().map(s -> s.getTotal().subtract(s.getReturnedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add); }

    private static String money(BigDecimal v) {
        return String.format(new Locale("en", "IN"), "%,.0f", r2(v));
    }

    private static Map<String, Object> insight(String tone, String text, String page) {
        return Map.of("tone", tone, "text", text, "page", page);
    }
}
