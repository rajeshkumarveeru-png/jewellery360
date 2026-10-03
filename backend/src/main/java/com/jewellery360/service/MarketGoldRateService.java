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
 * Reads the indicative Cuddalore market reference from GoodReturns.
 * The source currently renders the rates as plain text such as
 * "24K Gold /g ₹14,918". The parser deliberately accepts normal spaces,
 * non-breaking spaces and HTML-entity currency representations because the
 * exact HTML returned by an external site can vary between requests.
 */
@Service
public class MarketGoldRateService {
    private static final String SOURCE = "GoodReturns - Cuddalore";
    private static final String SOURCE_URL = "https://www.goodreturns.in/gold-rates/cuddalore.html";
    private static final Duration CACHE_FOR = Duration.ofMinutes(15);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private volatile Cached cached;

    public synchronized Map<String, Object> current() {
        if (cached != null && Duration.between(cached.loadedAt(), Instant.now()).compareTo(CACHE_FOR) < 0) {
            return cached.value();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SOURCE_URL))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/154 Safari/537.36 Jewellery360/1.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> parsed = parse(response.body());
                if (!rates(parsed).isEmpty()) {
                    cached = new Cached(parsed, Instant.now());
                    return parsed;
                }
            }
        } catch (Exception ignored) {
            // Header callers fall back to the latest persisted Jewellery360 rate.
        }

        // Keep the last successfully fetched live market value available even
        // if the external source has a short outage after the cache expires.
        if (cached != null && !rates(cached.value()).isEmpty()) {
            return cached.value();
        }

        return Map.of(
                "date", LocalDate.now(),
                "location", "Cuddalore",
                "source", SOURCE,
                "sourceUrl", SOURCE_URL,
                "rates", List.of(),
                "marketRates", List.of(),
                "available", false
        );
    }

    private Map<String, Object> parse(String html) {
        String text = html == null ? "" : html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&#8377;", "₹")
                .replace("&#x20B9;", "₹")
                .replace("&amp;", "&")
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        List<Map<String, Object>> rates = new ArrayList<>();
        addRate(rates, text, "24K");
        addRate(rates, text, "22K");
        addRate(rates, text, "18K");

        return Map.of(
                "date", LocalDate.now(),
                "location", "Cuddalore",
                "source", SOURCE,
                "sourceUrl", SOURCE_URL,
                "rates", rates,
                "marketRates", rates,
                "available", !rates.isEmpty()
        );
    }

    private void addRate(List<Map<String, Object>> rates, String text, String karat) {
        String k = Pattern.quote(karat);
        List<Pattern> patterns = List.of(
                Pattern.compile("(?i)\\b" + k + "\\s+Gold\\s*/\\s*g\\s*(?:₹|Rs\\.?|INR)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"),
                Pattern.compile("(?i)\\b" + k + "\\s+Gold\\s*(?:/\\s*g|per\\s+gram)\\s*(?:₹|Rs\\.?|INR)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"),
                Pattern.compile("(?i)\\b" + k + "(?:\\s+karat)?\\s+gold.*?(?:₹|Rs\\.?|INR)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?:per\\s+gram|/\\s*g)"),
                Pattern.compile("(?i)\\b" + k + "\\s*Gold\\s*(?:/\\s*g)?\\s*(?:₹|Rs\\.?|INR)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")
        );

        BigDecimal value = null;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) continue;
            try {
                BigDecimal candidate = new BigDecimal(matcher.group(1).replace(",", ""));
                if (candidate.compareTo(BigDecimal.valueOf(1000)) >= 0 && candidate.compareTo(BigDecimal.valueOf(100000)) <= 0) {
                    value = candidate;
                    break;
                }
            } catch (NumberFormatException ignored) {
                // Try the next parser pattern.
            }
        }

        if (value == null) return;

        Map<String, Object> rate = new LinkedHashMap<>();
        rate.put("karat", karat);
        rate.put("purity", karat);
        rate.put("ratePerGram", value);
        rate.put("ratePer10Gram", value.multiply(BigDecimal.TEN));
        rate.put("active", true);
        rates.add(rate);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rates(Map<String, Object> value) {
        Object raw = value == null ? null : value.get("rates");
        return raw instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
    }

    private record Cached(Map<String, Object> value, Instant loadedAt) {}
}
