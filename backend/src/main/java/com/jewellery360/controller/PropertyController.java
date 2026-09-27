package com.jewellery360.controller;

import com.jewellery360.domain.Company;
import com.jewellery360.domain.Property;
import com.jewellery360.repository.CompanyRepository;
import com.jewellery360.repository.PropertyRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.CompanyPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {
    private final PropertyRepository properties;
    private final CompanyRepository companies;
    private final CompanyPropertyService companyProperties;

    @GetMapping("/whatsapp")
    @Transactional
    public Map<String, Object> getWhatsApp(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) Long companyId) {
        Company company = resolveCompany(me, companyId);
        companyProperties.initializeWhatsAppDefaults(company);
        return response(company);
    }

    @PutMapping("/whatsapp")
    @Transactional
    public Map<String, Object> updateWhatsApp(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) Long companyId,
            @RequestBody WhatsAppRequest body) {
        Company company = resolveCompany(me, companyId);
        companyProperties.initializeWhatsAppDefaults(company);

        companyProperties.setWhatsAppProperty(company, CompanyPropertyService.WHATSAPP_ENABLED, Boolean.toString(body.enabled()));
        if (body.accessToken() != null && !body.accessToken().isBlank() && !"__KEEP_EXISTING__".equals(body.accessToken())) {
            companyProperties.setWhatsAppProperty(company, CompanyPropertyService.WHATSAPP_ACCESS_TOKEN, body.accessToken());
        }
        companyProperties.setWhatsAppProperty(company, CompanyPropertyService.WHATSAPP_PHONE_NUMBER_ID, clean(body.phoneNumberId()));
        companyProperties.setWhatsAppProperty(company, CompanyPropertyService.WHATSAPP_GRAPH_VERSION, defaultIfBlank(body.graphApiVersion(), "v23.0"));
        if (body.webhookVerifyToken() != null && !body.webhookVerifyToken().isBlank() && !"__KEEP_EXISTING__".equals(body.webhookVerifyToken())) {
            companyProperties.setWhatsAppProperty(company, CompanyPropertyService.WHATSAPP_WEBHOOK_VERIFY_TOKEN, body.webhookVerifyToken());
        }
        companyProperties.setWhatsAppProperty(company, CompanyPropertyService.WHATSAPP_INVOICE_TEMPLATE_NAME, defaultIfBlank(body.invoiceTemplateName(), "smartbill_invoice"));
        companyProperties.setWhatsAppProperty(company, CompanyPropertyService.WHATSAPP_TEMPLATE_LANGUAGE, defaultIfBlank(body.templateLanguage(), "en_US"));

        return response(company);
    }

    private Company resolveCompany(AuthenticatedUser me, Long requestedCompanyId) {
        if ("APP_ADMIN".equals(me.getRole())) {
            Long id = requestedCompanyId;
            if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required for WhatsApp settings.");
            return companies.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        }
        if (me.getCompanyId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company context required");
        if (requestedCompanyId != null && !me.getCompanyId().equals(requestedCompanyId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot edit another company");
        return companies.findById(me.getCompanyId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private Map<String, Object> response(Company company) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", company.getId());
        result.put("companyName", company.getName());
        result.put("enabled", value(company.getId(), CompanyPropertyService.WHATSAPP_ENABLED, "false"));
        result.put("accessTokenPresent", !value(company.getId(), CompanyPropertyService.WHATSAPP_ACCESS_TOKEN, "").isBlank());
        result.put("phoneNumberId", value(company.getId(), CompanyPropertyService.WHATSAPP_PHONE_NUMBER_ID, ""));
        result.put("graphApiVersion", value(company.getId(), CompanyPropertyService.WHATSAPP_GRAPH_VERSION, "v23.0"));
        result.put("webhookVerifyTokenPresent", !value(company.getId(), CompanyPropertyService.WHATSAPP_WEBHOOK_VERIFY_TOKEN, "").isBlank());
        result.put("invoiceTemplateName", value(company.getId(), CompanyPropertyService.WHATSAPP_INVOICE_TEMPLATE_NAME, "smartbill_invoice"));
        result.put("templateLanguage", value(company.getId(), CompanyPropertyService.WHATSAPP_TEMPLATE_LANGUAGE, "en_US"));
        return result;
    }

    private String value(Long companyId, String key, String fallback) {
        return properties.findByCompanyIdAndKeyAndActiveTrue(companyId, key).map(Property::getValue).orElse(fallback);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String defaultIfBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }

    public record WhatsAppRequest(
            boolean enabled,
            String accessToken,
            String phoneNumberId,
            String graphApiVersion,
            String webhookVerifyToken,
            String invoiceTemplateName,
            String templateLanguage) {}
}
