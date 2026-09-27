package com.jewellery360.service;

import com.jewellery360.domain.Company;
import com.jewellery360.domain.Property;
import com.jewellery360.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Company-scoped application properties.  Every approved company receives
 * the complete WhatsApp configuration key set so the Settings screen never
 * depends on environment-only configuration.
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

    @Transactional
    public void initializeWhatsAppDefaults(Company company) {
        if (company == null || company.getId() == null) return;
        whatsappDefaults().forEach((key, value) -> properties.findByCompanyIdAndKeyAndActiveTrue(company.getId(), key)
                .orElseGet(() -> save(company, key, value)));
    }

    @Transactional
    public void setWhatsAppProperty(Company company, String key, String value) {
        if (company == null || company.getId() == null) throw new IllegalArgumentException("Company is required");
        if (!whatsappDefaults().containsKey(key)) throw new IllegalArgumentException("Unsupported WhatsApp property: " + key);
        save(company, key, value == null ? "" : value.trim());
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
}
