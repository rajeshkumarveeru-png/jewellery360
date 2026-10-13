package com.jewellery360.support;

import com.jewellery360.domain.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/** API views of entities: only the fields a screen needs, never whole entity graphs (no password hashes, no lazy proxies). */
public final class Views {
    private Views() {}
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    public static Map<String, Object> map() { return new LinkedHashMap<>(); }

    public static Map<String, Object> customer(Customer c) {
        if (c == null) return null;
        Map<String, Object> m = map();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("phone", c.getPhone());
        m.put("email", c.getEmail());
        m.put("address", c.getAddress());
        m.put("gstin", c.getGstin());
        m.put("pan", c.getPan());
        m.put("birthday", c.getBirthday());
        m.put("anniversary", c.getAnniversary());
        m.put("notes", c.getNotes());
        m.put("active", c.isActive());
        m.put("branchId", c.getBranch() == null ? null : c.getBranch().getId());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    public static Map<String, Object> customerBrief(Customer c) {
        if (c == null) return null;
        Map<String, Object> m = map();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("phone", c.getPhone());
        return m;
    }

    public static Map<String, Object> purity(PurityMaster p) {
        if (p == null) return null;
        Map<String, Object> m = map();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("karat", p.getKarat());
        m.put("metal", p.getMetal());
        m.put("fineness", p.getFineness());
        m.put("description", p.getDescription());
        m.put("active", p.isActive());
        return m;
    }

    public static Map<String, Object> item(JewelleryItem i, boolean withCost) {
        Map<String, Object> m = map();
        m.put("id", i.getId());
        m.put("tagNo", i.getTagNo());
        m.put("barcode", i.getBarcode());
        m.put("huid", i.getHuid());
        m.put("certificateNo", i.getCertificateNo());
        JewelleryProduct p = i.getProduct();
        m.put("productId", p.getId());
        m.put("productName", p.getName());
        m.put("sku", p.getSku());
        m.put("hsnCode", p.getHsnCode());
        m.put("category", p.getCategory() == null ? null : p.getCategory().getName());
        m.put("design", p.getDesign() == null ? null : p.getDesign().getName());
        m.put("purity", purity(i.getPurity()));
        m.put("grossWeight", i.getGrossWeight());
        m.put("stoneWeight", i.getStoneWeight());
        m.put("netWeight", i.getNetWeight());
        m.put("stoneCount", i.getStoneCount());
        m.put("stoneDetails", i.getStoneDetails());
        m.put("stoneValue", i.getStoneValue());
        m.put("wastagePercent", i.getWastagePercent());
        m.put("makingType", i.getMakingType());
        m.put("makingValue", i.getMakingValue());
        m.put("otherCharge", i.getOtherCharge());
        m.put("mrp", i.getMrp());
        if (withCost) m.put("costPrice", i.getCostPrice());
        m.put("location", i.getLocation());
        m.put("status", i.getStatus());
        m.put("source", i.getSource());
        m.put("notes", i.getNotes());
        m.put("branchId", i.getBranch().getId());
        m.put("branchName", i.getBranch().getName());
        m.put("createdAt", i.getCreatedAt());
        m.put("soldAt", i.getSoldAt());
        m.put("ageDays", ageDays(i.getCreatedAt()));
        return m;
    }

    public static long ageDays(Instant since) {
        return since == null ? 0 : ChronoUnit.DAYS.between(since.atZone(INDIA).toLocalDate(), LocalDate.now(INDIA));
    }

    public static Map<String, Object> payment(Payment p) {
        Map<String, Object> m = map();
        m.put("id", p.getId());
        m.put("paymentNo", p.getPaymentNo());
        m.put("date", p.getPaymentDate());
        m.put("type", p.getPaymentType());
        m.put("mode", p.getPaymentMode());
        m.put("amount", p.getAmount());
        m.put("direction", p.direction());
        m.put("reference", p.getReference());
        m.put("notes", p.getNotes());
        m.put("customer", customerBrief(p.getCustomer()));
        m.put("supplier", p.getSupplier() == null ? null : p.getSupplier().getName());
        m.put("karigar", p.getKarigar() == null ? null : p.getKarigar().getName());
        m.put("saleId", p.getSale() == null ? null : p.getSale().getId());
        m.put("invoiceNo", p.getSale() == null ? null : p.getSale().getInvoiceNo());
        m.put("createdBy", p.getCreatedBy() == null ? null : p.getCreatedBy().getUsername());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    /** Display label combining bill status and payment status: Draft, Unpaid, Partially paid, Paid, Cancelled, Returned. */
    public static String billLabel(Sale s) {
        return switch (s.getStatus()) {
            case Sale.DRAFT -> "Draft";
            case Sale.CANCELLED -> "Cancelled";
            case Sale.RETURNED -> "Returned";
            default -> switch (s.getPaymentStatus()) {
                case "PAID" -> s.getStatus().equals(Sale.PARTIALLY_RETURNED) ? "Paid (part returned)" : "Paid";
                case "PARTIAL" -> "Partially paid";
                default -> "Unpaid";
            };
        };
    }

    public static Map<String, Object> saleSummary(Sale s) {
        Map<String, Object> m = map();
        m.put("id", s.getId());
        m.put("invoiceNo", s.getInvoiceNo());
        m.put("date", s.getSaleDate());
        m.put("createdAt", s.getCreatedAt());
        m.put("status", s.getStatus());
        m.put("paymentStatus", s.getPaymentStatus());
        m.put("label", billLabel(s));
        m.put("customer", customerBrief(s.getCustomer()));
        m.put("branchName", s.getBranch() == null ? null : s.getBranch().getName());
        m.put("total", s.getTotal());
        m.put("paid", s.getPaidAmount());
        m.put("returned", s.getReturnedAmount());
        m.put("balance", Sale.CANCELLED.equals(s.getStatus()) || Sale.DRAFT.equals(s.getStatus()) ? java.math.BigDecimal.ZERO : s.balance());
        m.put("cashier", s.getCreatedBy() == null ? null : s.getCreatedBy().getUsername());
        return m;
    }
}
