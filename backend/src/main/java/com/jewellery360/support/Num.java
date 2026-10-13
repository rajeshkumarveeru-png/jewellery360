package com.jewellery360.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Small helpers for money, weights and request bodies. Money and weights are kept at 3 decimals. */
public final class Num {
    private Num() {}
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static BigDecimal nz(BigDecimal x) { return x == null ? BigDecimal.ZERO : x; }
    public static BigDecimal r3(BigDecimal x) { return nz(x).setScale(3, RoundingMode.HALF_UP); }
    public static BigDecimal r2(BigDecimal x) { return nz(x).setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal pct(BigDecimal base, BigDecimal percent) { return r3(nz(base).multiply(nz(percent)).divide(HUNDRED, 6, RoundingMode.HALF_UP)); }
    public static boolean positive(BigDecimal x) { return x != null && x.signum() > 0; }

    public static BigDecimal dec(JsonNode n, String field) {
        if (n == null || !n.hasNonNull(field) || n.get(field).asText().isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(n.get(field).asText().trim()); } catch (NumberFormatException e) { throw Errors.bad(field + " must be a number."); }
    }
    public static BigDecimal decOrNull(JsonNode n, String field) {
        if (n == null || !n.hasNonNull(field) || n.get(field).asText().isBlank()) return null;
        return dec(n, field);
    }
    public static String text(JsonNode n, String field) {
        if (n == null || !n.hasNonNull(field)) return null;
        String v = n.get(field).asText().trim();
        return v.isEmpty() ? null : v;
    }
    public static String text(JsonNode n, String field, int max) {
        String v = text(n, field);
        if (v != null && v.length() > max) throw Errors.bad(field + " is too long (max " + max + " characters).");
        return v;
    }
    public static Long id(JsonNode n, String field) {
        if (n == null || !n.hasNonNull(field) || n.get(field).asText().isBlank()) return null;
        try { return Long.valueOf(n.get(field).asText().trim()); } catch (NumberFormatException e) { throw Errors.bad(field + " is invalid."); }
    }
    public static Long requireId(JsonNode n, String field, String label) {
        Long v = id(n, field);
        if (v == null) throw Errors.bad(label + " is required.");
        return v;
    }
    public static LocalDate date(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null) return null;
        try { return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v); } catch (Exception e) { throw Errors.bad(field + " must be a date (yyyy-mm-dd)."); }
    }
    public static boolean bool(JsonNode n, String field, boolean def) {
        return n == null || !n.hasNonNull(field) ? def : n.get(field).asBoolean(def);
    }
    public static BigDecimal nonNegative(BigDecimal v, String label) {
        if (v != null && v.signum() < 0) throw Errors.bad(label + " cannot be negative.");
        return nz(v);
    }
}
