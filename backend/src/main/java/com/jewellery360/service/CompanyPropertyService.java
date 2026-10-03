package com.jewellery360.service;

import com.jewellery360.domain.Company;
import com.jewellery360.domain.Property;
import com.jewellery360.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Company-scoped application properties.
 * WhatsApp and billing/tax configuration are persisted in the property table
 * so the same configuration is used across browsers, devices and invoice PDFs.
 */
@Service
@RequiredArgsConstructor
public class CompanyPropertyService {
    public static final String WHATSAPP_ENABLED = "WHATSAPP_ENABLED";
    public static final String WHATSAPP_ACCESS_TOKEN = "WHATSAPP_ACCESS_TOKEN";
    public static final String WHATSAPP_PHONE_NUMBER_ID = "WHATSAPP_PHONE_NUMBER_ID";
    public static final String WHATSAPP_GRAPH_VERSION = "WHATSAPP_GRAPH_VERSION";
    public static final String WHATSAPP_WEBHOOK_VERIFY_TOKEN = "WHATSAPP_WEBHOOK_VERIFY_TOKEN";
    public static final String WHATSAPP_INVOICE_TEMPLATE_NAME = "WHATSAPP_INVOICE_TEMPLATE_NAME";
    public static final String WHATSAPP_TEMPLATE_LANGUAGE = "WHATSAPP_TEMPLATE_LANGUAGE";

    public static final String TAX_ENABLED = "TAX_ENABLED";
    public static final String TAX_MODE = "TAX_MODE";
    public static final String TAX_RATE = "TAX_RATE";
    public static final String TAX_CGST_RATE = "TAX_CGST_RATE";
    public static final String TAX_SGST_RATE = "TAX_SGST_RATE";

    private final PropertyRepository properties;

    public Map<String, String> whatsappDefaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(WHATSAPP_ENABLED, "false");
        values.put(WHATSAPP_ACCESS_TOKEN, "");
        values.put(WHATSAPP_PHONE_NUMBER_ID, "");
        values.put(WHATSAPP_GRAPH_VERSION, "v23.0");
        values.put(WHATSAPP_WEBHOOK_VERIFY_TOKEN, "");
        values.put(WHATSAPP_INVOICE_TEMPLATE_NAME, "smartbill_invoice");
        values.put(WHATSAPP_TEMPLATE_LANGUAGE, "en_US");
        return values;
    }

    public Map<String, String> taxDefaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(TAX_ENABLED, "true");
        values.put(TAX_MODE, "GST");
        values.put(TAX_RATE, "3.00");
        values.put(TAX_CGST_RATE, "1.50");
        values.put(TAX_SGST_RATE, "1.50");
        return values;
    }

    @Transactional
    public void initializeWhatsAppDefaults(Company company) {
        if (company == null || company.getId() == null) return;
        whatsappDefaults().forEach((key, value) -> properties.findByCompanyIdAndKeyAndActiveTrue(company.getId(), key)
                .orElseGet(() -> save(company, key, value)));
    }

    @Transactional
    public void initializeTaxDefaults(Company company) {
        if (company == null || company.getId() == null) return;
        taxDefaults().forEach((key, value) -> properties.findByCompanyIdAndKeyAndActiveTrue(company.getId(), key)
                .orElseGet(() -> save(company, key, value)));
    }

    @Transactional
    public void setWhatsAppProperty(Company company, String key, String value) {
        if (company == null || company.getId() == null) throw new IllegalArgumentException("Company is required");
        if (!whatsappDefaults().containsKey(key)) throw new IllegalArgumentException("Unsupported WhatsApp property: " + key);
        save(company, key, value == null ? "" : value.trim());
    }

    @Transactional
    public void setTaxProperty(Company company, String key, String value) {
        if (company == null || company.getId() == null) throw new IllegalArgumentException("Company is required");
        if (!taxDefaults().containsKey(key)) throw new IllegalArgumentException("Unsupported tax property: " + key);
        save(company, key, value == null ? "" : value.trim());
    }

    @Transactional
    public TaxSettings getTaxSettings(Company company) {
        if (company == null || company.getId() == null) {
            return TaxSettings.defaults();
        }
        initializeTaxDefaults(company);
        boolean enabled = Boolean.parseBoolean(value(company.getId(), TAX_ENABLED, "true"));
        String mode = value(company.getId(), TAX_MODE, "GST").toUpperCase();
        if (!"GST".equals(mode) && !"CGST_SGST".equals(mode)) mode = "GST";

        BigDecimal rate = decimal(value(company.getId(), TAX_RATE, "3.00"));
        BigDecimal cgst = decimal(value(company.getId(), TAX_CGST_RATE, "1.50"));
        BigDecimal sgst = decimal(value(company.getId(), TAX_SGST_RATE, "1.50"));

        if (rate.signum() < 0) rate = BigDecimal.ZERO;
        if (cgst.signum() < 0) cgst = BigDecimal.ZERO;
        if (sgst.signum() < 0) sgst = BigDecimal.ZERO;

        return new TaxSettings(enabled, mode, rate, cgst, sgst);
    }

    private String value(Long companyId, String key, String fallback) {
        return properties.findByCompanyIdAndKeyAndActiveTrue(companyId, key)
                .map(Property::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value == null ? "0" : value.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private Property save(Company company, String key, String value) {
        Property p = properties.findByCompanyIdAndKeyAndActiveTrue(company.getId(), key).orElseGet(Property::new);
        p.setCompany(company);
        p.setKey(key);
        p.setValue(value);
        p.setType("STRING");
        p.setActive(true);
        return properties.save(p);
    }

    public record TaxSettings(
            boolean enabled,
            String mode,
            BigDecimal rate,
            BigDecimal cgstRate,
            BigDecimal sgstRate) {
        public static TaxSettings defaults() {
            return new TaxSettings(true, "GST", new BigDecimal("3.00"), new BigDecimal("1.50"), new BigDecimal("1.50"));
        }

        public BigDecimal totalRate() {
            if (!enabled) return BigDecimal.ZERO;
            return "CGST_SGST".equalsIgnoreCase(mode)
                    ? cgstRate.add(sgstRate)
                    : rate;
        }
    }
}
