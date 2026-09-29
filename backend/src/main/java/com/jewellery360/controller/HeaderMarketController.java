package com.jewellery360.controller;

import com.jewellery360.domain.GoldRate;
import com.jewellery360.repository.GoldRateRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.MarketGoldRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/header")
@RequiredArgsConstructor
public class HeaderMarketController {

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private final GoldRateRepository goldRates;
    private final MarketGoldRateService marketGoldRates;

    @GetMapping("/gold-rates")
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

        List<Map<String, Object>> rates = List.of();
        if (companyId != null && branchId != null) {
            rates = goldRates
                    .findByCompanyIdAndBranchIdAndRateDateOrderByRatePerGramDesc(
                            companyId, branchId, today)
                    .stream()
                    .map(this::toRate)
                    .toList();
        }

        Map<String, Object> market = marketGoldRates.current();
        List<?> marketRates = market.get("rates") instanceof List<?> list ? list : List.of();

        return Map.of(
                "date", today,
                "rates", rates,
                "marketRates", marketRates,
                "marketSource", market.getOrDefault("source", ""),
                "marketSourceUrl", market.getOrDefault("sourceUrl", ""),
                "marketLocation", market.getOrDefault("location", "Cuddalore"),
                "marketAvailable", market.getOrDefault("available", false)
        );
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
                "active", rate.isActive()
        );
    }
}
