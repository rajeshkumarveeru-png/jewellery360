package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.domain.Branch;
import com.jewellery360.domain.GoldRate;
import com.jewellery360.domain.PurityMaster;
import com.jewellery360.repository.CustomOrderRepository;
import com.jewellery360.repository.GoldRateRepository;
import com.jewellery360.repository.PurityMasterRepository;
import com.jewellery360.repository.SaleRepository;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Num;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * The shop's own selling rates per purity, per branch, per day. They are only ever changed by a person: the market feed is
 * shown as a reference the user can copy, never written automatically. Bills copy the rate into each line, so changing a
 * rate never changes an existing bill.
 */
@Service
@RequiredArgsConstructor
public class GoldRateService {
    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final GoldRateRepository rates;
    private final PurityMasterRepository purities;
    private final CustomOrderRepository orders;
    private final SaleRepository sales;
    private final MarketGoldRateService market;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    public static LocalDate today() { return LocalDate.now(INDIA); }

    /** Rate per gram for a purity in a branch on a day (the latest saved on or before that day), or null if none was ever saved. */
    @Transactional(readOnly = true)
    public GoldRate rateFor(Branch branch, PurityMaster purity, LocalDate day) {
        return rateMap(branch.getId(), day).get(purity.getId());
    }

    @Transactional(readOnly = true)
    public Map<Long, GoldRate> rateMap(Long branchId, LocalDate day) {
        Map<Long, GoldRate> out = new HashMap<>();
        for (GoldRate r : rates.latestForBranch(branchId, day)) out.put(r.getPurity().getId(), r);
        return out;
    }

    /** Today's board: every active purity with its rate, whether it was set today, and the change from the previous rate. */
    @Transactional(readOnly = true)
    public Map<String, Object> board(TenantContext.Ctx ctx) {
        LocalDate today = today();
        Map<Long, GoldRate> current = ctx.branch() == null ? Map.of() : rateMap(ctx.branchId(), today);
        Map<Long, GoldRate> previous = ctx.branch() == null ? Map.of() : rateMap(ctx.branchId(), today.minusDays(1));
        List<Map<String, Object>> rows = new ArrayList<>();
        int missing = 0;
        for (PurityMaster p : purities.findByCompanyIdOrderByMetalAscFinenessDesc(ctx.companyId())) {
            if (!p.isActive()) continue;
            GoldRate r = current.get(p.getId());
            GoldRate before = previous.get(p.getId());
            boolean setToday = r != null && today.equals(r.getRateDate());
            if (!setToday) missing++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("purityId", p.getId());
            row.put("purity", p.getName());
            row.put("karat", p.getKarat());
            row.put("metal", p.getMetal());
            row.put("fineness", p.getFineness());
            row.put("ratePerGram", r == null ? null : r.getRatePerGram());
            row.put("rateDate", r == null ? null : r.getRateDate());
            row.put("setToday", setToday);
            row.put("source", r == null ? null : r.getSource());
            row.put("updatedAt", r == null ? null : r.getUpdatedAt());
            row.put("updatedBy", r == null || r.getUpdatedBy() == null ? null : r.getUpdatedBy().getUsername());
            BigDecimal prev = before == null || !setToday ? null : before.getRatePerGram();
            row.put("previousRate", prev);
            row.put("change", r == null || prev == null ? null : r.getRatePerGram().subtract(prev));
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today);
        out.put("branchId", ctx.branchId());
        out.put("rates", rows);
        out.put("missingToday", missing);
        return out;
    }

    /**
     * Saves rates for a day: {date?, rates:[{purityId, ratePerGram, source?}]}. Only purities included are changed.
     * Returns the new board plus what the change affects (open orders without a locked rate, draft bills).
     */
    @Transactional
    public Map<String, Object> save(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requireManage(ctx.me(), "GOLD_RATES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        LocalDate day = Optional.ofNullable(Num.date(body, "date")).orElse(today());
        if (day.isAfter(today())) throw Errors.bad("Rates cannot be set for a future date.");
        JsonNode list = body.get("rates");
        if (list == null || !list.isArray() || list.isEmpty()) throw Errors.bad("Enter at least one rate.");
        List<Map<String, Object>> changes = new ArrayList<>();
        for (JsonNode n : list) {
            Long purityId = Num.requireId(n, "purityId", "Purity");
            PurityMaster purity = purities.findById(purityId).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, purity.getCompany());
            BigDecimal value = Num.r3(Num.dec(n, "ratePerGram"));
            if (value.signum() <= 0) throw Errors.bad("Rate for " + purity.getName() + " must be more than 0.");
            GoldRate r = rates.findByCompanyIdAndBranchIdAndPurityIdAndRateDate(ctx.companyId(), ctx.branchId(), purityId, day).orElse(null);
            BigDecimal before = r == null ? null : r.getRatePerGram();
            if (r == null) {
                r = new GoldRate();
                r.setCompany(ctx.company());
                r.setBranch(ctx.branch());
                r.setPurity(purity);
                r.setRateDate(day);
            }
            if (before != null && before.compareTo(value) == 0) continue;
            r.setRatePerGram(value);
            r.setSource("MARKET".equalsIgnoreCase(Num.text(n, "source")) ? "MARKET" : "MANUAL");
            r.setUpdatedBy(tenant.user(ctx.me()));
            r.setUpdatedAt(Instant.now());
            rates.save(r);
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("purity", purity.getName());
            change.put("before", before);
            change.put("after", value);
            changes.add(change);
            audit.log(ctx, "GOLD_RATE_SET", "GoldRate", r.getId(), before == null ? null : Map.of("ratePerGram", before, "date", day.toString()),
                    Map.of("purity", purity.getName(), "ratePerGram", value, "date", day.toString()));
        }
        Map<String, Object> out = board(ctx);
        out.put("changed", changes);
        out.put("impact", impact(ctx));
        return out;
    }

    /** Rate impact: open custom orders that will be billed at the delivery-day rate, and draft bills that will be re-priced. */
    @Transactional(readOnly = true)
    public Map<String, Object> impact(TenantContext.Ctx ctx) {
        var open = orders.list(ctx.companyId(), ctx.branchId(), null).stream()
                .filter(o -> List.of("ORDERED", "IN_PROGRESS", "READY").contains(o.getStatus()))
                .toList();
        long unlocked = open.stream().filter(o -> o.getLockedRate().signum() == 0).count();
        long drafts = sales.countByCompanyIdAndStatus(ctx.companyId(), "DRAFT");
        return Map.of("openOrders", open.size(), "ordersWithoutLockedRate", unlocked, "draftBills", drafts);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(TenantContext.Ctx ctx, int days) {
        if (ctx.branch() == null) return List.of();
        LocalDate to = today();
        LocalDate from = to.minusDays(Math.max(1, Math.min(days, 400)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GoldRate r : rates.findByBranchIdAndRateDateBetweenOrderByRateDateDesc(ctx.branchId(), from, to)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("date", r.getRateDate());
            row.put("purityId", r.getPurity().getId());
            row.put("purity", r.getPurity().getName());
            row.put("metal", r.getPurity().getMetal());
            row.put("ratePerGram", r.getRatePerGram());
            row.put("source", r.getSource());
            row.put("updatedAt", r.getUpdatedAt());
            row.put("updatedBy", r.getUpdatedBy() == null ? null : r.getUpdatedBy().getUsername());
            out.add(row);
        }
        return out;
    }

    /** Indicative market reference (never saved automatically). For purities of the company it suggests a matching value. */
    @Transactional(readOnly = true)
    public Map<String, Object> marketReference(TenantContext.Ctx ctx) {
        Map<String, Object> m = market.current();
        List<Map<String, Object>> suggestions = new ArrayList<>();
        Object raw = m.get("rates");
        if (raw instanceof List<?> list) {
            for (PurityMaster p : purities.findByCompanyIdOrderByMetalAscFinenessDesc(ctx.companyId())) {
                if (!p.isActive() || !"GOLD".equals(p.getMetal())) continue;
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> r)) continue;
                    String karat = String.valueOf(r.get("karat")).replaceAll("[^0-9]", "");
                    String mine = Objects.toString(p.getKarat(), p.getName()).replaceAll("[^0-9]", "");
                    if (!karat.isEmpty() && karat.equals(mine)) {
                        suggestions.add(Map.of("purityId", p.getId(), "purity", p.getName(),
                                "ratePerGram", new BigDecimal(String.valueOf(r.get("ratePerGram"))).setScale(3, RoundingMode.HALF_UP)));
                    }
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(m);
        out.put("suggestions", suggestions);
        out.put("note", "Market figures are an indicative reference only. Your saved rates are what bills use.");
        return out;
    }
}
