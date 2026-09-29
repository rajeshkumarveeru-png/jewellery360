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
    private static final Pattern RATE = Pattern.compile(
            "(?i)(24K|22K|18K)\\s*Gold\\s*/g\\s*₹\\s*([0-9,]+(?:\\.[0-9]+)?)");

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
                "sourceUrl", SOURCE_URL, "rates", List.of(), "available", false);
    }

    private Map<String, Object> parse(String html) {
        String text = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ");

        Matcher matcher = RATE.matcher(text);
        List<Map<String, Object>> rates = new ArrayList<>();
        while (matcher.find()) {
            Map<String, Object> rate = new LinkedHashMap<>();
            rate.put("karat", matcher.group(1).toUpperCase());
            rate.put("purity", matcher.group(1).toUpperCase());
            rate.put("ratePerGram", new BigDecimal(matcher.group(2).replace(",", "")));
            rate.put("ratePer10Gram", new BigDecimal(matcher.group(2).replace(",", "")).multiply(BigDecimal.TEN));
            rate.put("active", true);
            rates.add(rate);
        }

        return Map.of("date", LocalDate.now(), "location", "Cuddalore", "source", SOURCE,
                "sourceUrl", SOURCE_URL, "rates", rates, "available", !rates.isEmpty());
    }

    private record Cached(Map<String, Object> value, Instant loadedAt) {}
}
