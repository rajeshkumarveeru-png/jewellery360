package com.jewellery360.controller;

import com.jewellery360.domain.Branch;
import com.jewellery360.domain.Company;
import com.jewellery360.domain.GoldRate;
import com.jewellery360.domain.PurityMaster;
import com.jewellery360.repository.BranchRepository;
import com.jewellery360.repository.CompanyRepository;
import com.jewellery360.repository.GoldRateRepository;
import com.jewellery360.repository.PurityMasterRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.MarketGoldRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/header")
@RequiredArgsConstructor
public class HeaderMarketController {

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private final GoldRateRepository goldRates;
    private final MarketGoldRateService marketGoldRates;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final PurityMasterRepository purities;

    @GetMapping("/gold-rates")
    @Transactional
    public Map<String, Object> goldRates(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestHeader(value = "X-Company-Id", required = false) Long headerCompanyId,
            @RequestHeader(value = "X-Branch-Id", required = false) Long headerBranchId) {

        Long companyId = me == null ? null : me.getCompanyId();
        Long branchId = me == null ? null : me.getBranchId();

        if (me != null && "APP_ADMIN".equals(me.getRole())) {
            companyId = headerCompanyId;
            branchId = headerBranchId;
        }

        LocalDate today = LocalDate.now(INDIA_ZONE);

        Map<String, Object> market = marketGoldRates.current();
        List<Map<String, Object>> liveMarketRates = marketRates(market);

        // Persist every successful live market fetch for the active company/branch.
        // This makes the current market rate immediately available to Billing and
        // gives the application a database fallback during a temporary source outage.
        if (companyId != null && branchId != null && !liveMarketRates.isEmpty()) {
            persistLiveMarketRates(companyId, branchId, today, liveMarketRates);
        }

        List<Map<String, Object>> rates = List.of();
        String rateSource = "";

        if (companyId != null && branchId != null) {
            List<GoldRate> todayRates = goldRates
                    .findByCompanyIdAndBranchIdAndRateDateOrderByRatePerGramDesc(companyId, branchId, today);
            rates = todayRates.stream().map(this::toRate).toList();
            rateSource = rates.isEmpty() ? "" : Objects.toString(rates.get(0).get("source"), "Saved PostgreSQL GoldRate");

            // If today's record is not available, use the latest saved rate per purity.
            // It is explicitly labelled as a fallback, never as a live market quote.
            if (rates.isEmpty()) {
                Map<String, GoldRate> latest = new LinkedHashMap<>();
                for (GoldRate row : goldRates.findByCompanyId(companyId)) {
                    if (row.getBranch() == null || !Objects.equals(row.getBranch().getId(), branchId)) continue;
                    if (row.getPurity() == null || row.getRatePerGram() == null || row.getRatePerGram().signum() <= 0) continue;
                    String key = normalizeKarat(row.getPurity().getKarat(), row.getPurity().getName());
                    GoldRate existing = latest.get(key);
                    if (existing == null || row.getRateDate().isAfter(existing.getRateDate())) latest.put(key, row);
                }
                rates = latest.values().stream().sorted(Comparator.comparingInt(this::karatNumber)).map(this::toFallbackRate).toList();
                if (!rates.isEmpty()) rateSource = "Saved PostgreSQL GoldRate (fallback)";
            }
        }

        // Prefer the actual live market feed for the header. If the database has just
        // been persisted, both representations contain the same current value.
        List<Map<String, Object>> displayMarket = !liveMarketRates.isEmpty() ? liveMarketRates : rates;
        boolean liveAvailable = !liveMarketRates.isEmpty();

        return Map.of(
                "date", today,
                "rates", rates,
                "marketRates", displayMarket,
                "marketSource", liveAvailable
                        ? market.getOrDefault("source", "GoodReturns - Cuddalore")
                        : (rateSource.isBlank() ? market.getOrDefault("source", "GoodReturns - Cuddalore") : rateSource),
                "marketSourceUrl", market.getOrDefault("sourceUrl", "https://www.goodreturns.in/gold-rates/cuddalore.html"),
                "marketLocation", market.getOrDefault("location", "Cuddalore"),
                "marketAvailable", liveAvailable,
                "marketFallback", !liveAvailable && !rates.isEmpty()
        );
    }

    private void persistLiveMarketRates(Long companyId, Long branchId, LocalDate date, List<Map<String, Object>> liveRates) {
        Company company = companies.findById(companyId).orElse(null);
        Branch branch = branches.findById(branchId).orElse(null);
        if (company == null || branch == null || !Objects.equals(branch.getCompany().getId(), companyId)) return;

        List<PurityMaster> companyPurities = purities.findByCompanyId(companyId);
        for (Map<String, Object> live : liveRates) {
            String karat = normalizeKarat(Objects.toString(live.get("karat"), ""), Objects.toString(live.get("purity"), ""));
            BigDecimal rate = decimal(live.get("ratePerGram"));
            if (karat.isBlank() || rate.signum() <= 0) continue;

            PurityMaster purity = companyPurities.stream()
                    .filter(p -> karat.equals(normalizeKarat(p.getKarat(), p.getName())))
                    .findFirst().orElse(null);
            if (purity == null) continue;

            GoldRate row = goldRates.findByCompanyIdAndBranchIdAndPurityIdAndRateDate(companyId, branchId, purity.getId(), date)
                    .orElseGet(GoldRate::new);
            row.setCompany(company);
            row.setBranch(branch);
            row.setPurity(purity);
            row.setRateDate(date);
            row.setRatePerGram(rate.setScale(3, java.math.RoundingMode.HALF_UP));
            row.setSource("GoodReturns - Cuddalore");
            row.setActive(true);
            goldRates.save(row);
        }
    }

    private Map<String, Object> toRate(GoldRate rate) {
        String name = rate.getPurity() == null ? "Gold" : rate.getPurity().getName();
        String karat = rate.getPurity() == null ? "" : String.valueOf(rate.getPurity().getKarat());
        BigDecimal value = rate.getRatePerGram();
        return Map.of(
                "purity", name,
                "karat", karat == null || "null".equals(karat) ? "" : karat,
                "ratePerGram", value,
                "ratePer10Gram", value.multiply(BigDecimal.TEN),
                "source", Objects.toString(rate.getSource(), "Saved PostgreSQL GoldRate"),
                "active", rate.isActive()
        );
    }

    private Map<String, Object> toFallbackRate(GoldRate rate) {
        Map<String, Object> result = new LinkedHashMap<>(toRate(rate));
        result.put("source", "Saved PostgreSQL GoldRate (fallback)");
        result.put("rateDate", rate.getRateDate());
        return result;
    }

    private List<Map<String, Object>> marketRates(Map<String, Object> market) {
        Object raw = market == null ? null : market.get("rates");
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : list) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> item = new LinkedHashMap<>();
                map.forEach((key, val) -> item.put(String.valueOf(key), val));
                result.add(item);
            }
        }
        result.sort(Comparator.comparingInt(x -> karatNumber(Objects.toString(x.get("karat"), Objects.toString(x.get("purity"), "")))));
        return result;
    }

    private String normalizeKarat(String karat, String name) {
        String raw = Objects.toString(karat, "") + " " + Objects.toString(name, "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(24|22|18|14)").matcher(raw);
        return m.find() ? m.group(1) + "K" : raw.trim().toUpperCase();
    }

    private int karatNumber(GoldRate rate) {
        return karatNumber(normalizeKarat(rate.getPurity() == null ? "" : rate.getPurity().getKarat(), rate.getPurity() == null ? "" : rate.getPurity().getName()));
    }

    private int karatNumber(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private BigDecimal decimal(Object value) {
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
