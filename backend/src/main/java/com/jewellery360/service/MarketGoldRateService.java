package com.jewellery360.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an indicative Cuddalore market reference from GoodReturns.
 * The result is cached briefly so every header refresh does not hit the external site.
 * If the external source is unavailable, callers can fall back to saved Jewellery360 rates.
 */
@Service
public class MarketGoldRateService {
    private static final String SOURCE = "GoodReturns - Cuddalore";
    private static final String SOURCE_URL = "https://www.goodreturns.in/gold-rates/cuddalore.html";
    private static final Duration CACHE_FOR = Duration.ofMinutes(15);
    private static final Pattern RATE = Pattern.compile("(?i)(24K|22K|18K)\\s*(?:Gold|Carat Gold|Karat Gold)?[^₹\\d]{0,60}₹?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private volatile Cached cached;

    public synchronized Map<String, Object> current() {
        if (cached != null && Duration.between(cached.loadedAt(), Instant.now()).compareTo(CACHE_FOR) < 0) {
            return cached.value();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SOURCE_URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 Jewellery360/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> parsed = parse(response.body());
                if (!((List<?>) parsed.get("rates")).isEmpty()) {
                    cached = new Cached(parsed, Instant.now());
                    return parsed;
                }
            }
        } catch (Exception ignored) {
            // Header callers will use saved database rates when the market source is unavailable.
        }

        return Map.of("date", LocalDate.now(), "location", "Cuddalore", "source", SOURCE,
                "sourceUrl", SOURCE_URL, "rates", List.of(), "marketRates", List.of(), "available", false);
    }

    private Map<String, Object> parse(String html) {
        String text = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ");

        // GoodReturns currently exposes the Cuddalore rates as:
        // 24K Gold /g ₹14,880, 22K Gold /g ₹13,640, 18K Gold /g ₹11,410.
        // Keep each purity anchored to its own label so numbers from the adjacent
        // table columns cannot be accidentally captured as the rate.
        List<Map<String, Object>> rates = new ArrayList<>();
        addRate(rates, text, "24K");
        addRate(rates, text, "22K");
        addRate(rates, text, "18K");

        return Map.of("date", LocalDate.now(), "location", "Cuddalore", "source", SOURCE,
                "sourceUrl", SOURCE_URL, "rates", rates, "marketRates", rates, "available", !rates.isEmpty());
    }

    private void addRate(List<Map<String, Object>> rates, String text, String karat) {
        Pattern p = Pattern.compile("(?i)\\b" + karat + "\\s+Gold\\s*/\\s*g\\s*₹?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            // Fallback for the sentence form used by the page summary.
            p = Pattern.compile("(?i)" + karat + "(?:\\s+karat)?\\s+gold.*?₹\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+per gram");
            m = p.matcher(text);
        }
        if (!m.find()) return;

        BigDecimal value;
        try {
            value = new BigDecimal(m.group(1).replace(",", ""));
        } catch (NumberFormatException ex) {
            return;
        }
        // Reject table/header numbers accidentally matched as a rate.
        if (value.compareTo(BigDecimal.valueOf(1000)) < 0 || value.compareTo(BigDecimal.valueOf(100000)) > 0) return;

        Map<String, Object> rate = new LinkedHashMap<>();
        rate.put("karat", karat);
        rate.put("purity", karat);
        rate.put("ratePerGram", value);
        rate.put("ratePer10Gram", value.multiply(BigDecimal.TEN));
        rate.put("active", true);
        rates.add(rate);
    }

    private record Cached(Map<String, Object> value, Instant loadedAt) {}
}
