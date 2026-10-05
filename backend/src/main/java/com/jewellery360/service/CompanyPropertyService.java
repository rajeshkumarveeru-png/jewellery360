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

    public static final String BUSINESS_PHONE = "BUSINESS_PHONE";
    public static final String BUSINESS_EMAIL = "BUSINESS_EMAIL";
    public static final String BUSINESS_GSTIN = "BUSINESS_GSTIN";
    public static final String PRINT_FORMAT = "PRINT_FORMAT";
    public static final String SKU_PREFIX = "SKU_PREFIX";
    public static final String INVOICE_TEMPLATE = "INVOICE_TEMPLATE";

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

    public Map<String, String> businessDefaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(BUSINESS_PHONE, "");
        values.put(BUSINESS_EMAIL, "");
        values.put(BUSINESS_GSTIN, "");
        values.put(PRINT_FORMAT, "A4");
        values.put(SKU_PREFIX, "");
        values.put(INVOICE_TEMPLATE, "CLASSIC");
        return values;
    }

    @Transactional
    public void initializeBusinessDefaults(Company company) {
        if (company == null || company.getId() == null) return;
        businessDefaults().forEach((key, value) -> properties.findByCompanyIdAndKeyAndActiveTrue(company.getId(), key)
                .orElseGet(() -> save(company, key, value)));
    }

    @Transactional
    public void setBusinessProperty(Company company, String key, String value) {
        if (company == null || company.getId() == null) throw new IllegalArgumentException("Company is required");
        if (!businessDefaults().containsKey(key)) throw new IllegalArgumentException("Unsupported business property: " + key);
        save(company, key, value == null ? "" : value.trim());
    }

    /**
     * Business profile used by the header, receipts and PDFs.
     * Phone, e-mail and GSTIN fall back to the values captured on the company record when the property is blank.
     */
    @Transactional
    public BusinessProfile getBusinessProfile(Company company) {
        if (company == null || company.getId() == null) {
            return new BusinessProfile("", "", "", "", "A4", "", "CLASSIC");
        }
        initializeBusinessDefaults(company);
        Long id = company.getId();
        String phone = value(id, BUSINESS_PHONE, "");
        String email = value(id, BUSINESS_EMAIL, "");
        String gstin = value(id, BUSINESS_GSTIN, "");
        if (phone.isBlank() && company.getPhone() != null) phone = company.getPhone();
        if (email.isBlank() && company.getEmail() != null) email = company.getEmail();
        if (gstin.isBlank() && company.getGstin() != null) gstin = company.getGstin();
        String format = value(id, PRINT_FORMAT, "A4").toUpperCase();
        if (!"A4".equals(format) && !"50MM".equals(format) && !"80MM".equals(format)) format = "A4";
        String template = value(id, INVOICE_TEMPLATE, "CLASSIC").toUpperCase();
        if (!java.util.List.of("CLASSIC", "MODERN", "ROYAL", "COMPACT", "FORMAL").contains(template)) template = "CLASSIC";
        return new BusinessProfile(company.getName() == null ? "" : company.getName(), phone, email, gstin, format,
                value(id, SKU_PREFIX, "").toUpperCase(), template);
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

    /** Company identity + document settings. printFormat is one of A4, 50MM (thermal) or 80MM (thermal). */
    public record BusinessProfile(String companyName, String phone, String email, String gstin, String printFormat, String skuPrefix, String invoiceTemplate) {}

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
