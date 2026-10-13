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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {
    private final com.jewellery360.service.PermissionService permissions;
    private final PropertyRepository properties;
    private final CompanyRepository companies;
    private final com.jewellery360.repository.AppUserRepository users;
    private final CompanyPropertyService companyProperties;

    @GetMapping("/whatsapp")
    @Transactional
    public Map<String, Object> getWhatsApp(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) Long companyId) {
        requireSettings(me);
        Company company = resolveCompany(me, companyId);
        companyProperties.initializeWhatsAppDefaults(company);
        return response(company);
    }

    @GetMapping("/tax")
    @Transactional
    public Map<String, Object> getTax(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) Long companyId) {
        Company company = resolveCompany(me, companyId);
        CompanyPropertyService.TaxSettings tax = companyProperties.getTaxSettings(company);
        return taxResponse(company, tax);
    }

    @PutMapping("/tax")
    @Transactional
    public Map<String, Object> updateTax(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) Long companyId,
            @RequestBody TaxRequest body) {
        requireSettings(me);
        Company company = resolveCompany(me, companyId);
        companyProperties.initializeTaxDefaults(company);

        String mode = body.mode() == null ? "GST" : body.mode().trim().toUpperCase();
        if (!"GST".equals(mode) && !"CGST_SGST".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax mode must be GST or CGST_SGST");
        }

        BigDecimal rate = safeRate(body.rate(), "GST rate");
        BigDecimal cgstRate = safeRate(body.cgstRate(), "CGST rate");
        BigDecimal sgstRate = safeRate(body.sgstRate(), "SGST rate");

        companyProperties.setTaxProperty(company, CompanyPropertyService.TAX_ENABLED, Boolean.toString(body.enabled()));
        companyProperties.setTaxProperty(company, CompanyPropertyService.TAX_MODE, mode);
        companyProperties.setTaxProperty(company, CompanyPropertyService.TAX_RATE, rate.toPlainString());
        companyProperties.setTaxProperty(company, CompanyPropertyService.TAX_CGST_RATE, cgstRate.toPlainString());
        companyProperties.setTaxProperty(company, CompanyPropertyService.TAX_SGST_RATE, sgstRate.toPlainString());

        return taxResponse(company, companyProperties.getTaxSettings(company));
    }

    /** Business profile: company name (read only), business phone, corporate e-mail, GSTIN, print layout and SKU prefix. */
    @GetMapping("/business")
    @Transactional
    public Map<String, Object> getBusiness(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) Long companyId) {
        Company company = resolveCompany(me, companyId);
        return businessResponse(company, companyProperties.getBusinessProfile(company));
    }

    @PutMapping("/business")
    @Transactional
    public Map<String, Object> updateBusiness(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) Long companyId,
            @RequestBody BusinessRequest body) {
        requireSettings(me);
        Company company = resolveCompany(me, companyId);
        companyProperties.initializeBusinessDefaults(company);

        // Global brand: the company name set here is what the header, login portal and PDF receipts show.
        String newName = clean(body.companyName());
        if (!newName.isEmpty()) {
            if (newName.length() < 2 || newName.length() > 150) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company name must be between 2 and 150 characters");
            }
            if (!newName.equalsIgnoreCase(company.getName()) && companies.existsByNameIgnoreCase(newName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Another company already uses this name");
            }
            company.setName(newName);
            companies.save(company);
        }

        String phone = clean(body.phone());
        if (!phone.isEmpty() && !phone.matches("^[+0-9][0-9 ()-]{6,19}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business phone must contain 7-20 digits (optional + prefix)");
        }
        String email = clean(body.email());
        if (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corporate e-mail is not a valid address");
        }
        String gstin = clean(body.gstin()).toUpperCase();
        if (!gstin.isEmpty() && !gstin.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GSTIN must be 15 characters, e.g. 33ABCDE1234F1Z5");
        }
        String format = defaultIfBlank(body.printFormat(), "A4").toUpperCase();
        if (!"A4".equals(format) && !"50MM".equals(format) && !"80MM".equals(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Print layout must be A4, 50MM or 80MM");
        }
        String skuPrefix = clean(body.skuPrefix()).toUpperCase();
        if (!skuPrefix.isEmpty() && !skuPrefix.matches("^[A-Z0-9][A-Z0-9-]{0,11}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU prefix may contain letters, digits and hyphen (max 12 characters)");
        }

        String template = defaultIfBlank(body.invoiceTemplate(), "CLASSIC").toUpperCase();
        if (!java.util.Arrays.asList(InvoiceTemplates.KEYS).contains(template)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice design must be one of " + String.join(", ", InvoiceTemplates.KEYS));
        }
        companyProperties.setBusinessProperty(company, CompanyPropertyService.INVOICE_TEMPLATE, template);
        companyProperties.setBusinessProperty(company, CompanyPropertyService.BUSINESS_PHONE, phone);
        companyProperties.setBusinessProperty(company, CompanyPropertyService.BUSINESS_EMAIL, email);
        companyProperties.setBusinessProperty(company, CompanyPropertyService.BUSINESS_GSTIN, gstin);
        companyProperties.setBusinessProperty(company, CompanyPropertyService.PRINT_FORMAT, format);
        companyProperties.setBusinessProperty(company, CompanyPropertyService.SKU_PREFIX, skuPrefix);
        return businessResponse(company, companyProperties.getBusinessProfile(company));
    }

    @PutMapping("/whatsapp")
    @Transactional
    public Map<String, Object> updateWhatsApp(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) Long companyId,
            @RequestBody WhatsAppRequest body) {
        requireSettings(me);
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

    /* ---------------------------------------------------------------- menu layout */

    /** Company menu: order, groups, hidden pages and which roles see each page. Access itself is still decided by permissions. */
    @GetMapping("/menu")
    public Map<String, Object> getMenu(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long companyId) throws Exception {
        Company company = resolveCompany(me, companyId);
        String json = companyProperties.getMenuLayout(company);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("custom", json != null);
        out.put("items", json == null ? defaultMenu() : JSON.readValue(json, java.util.List.class));
        out.put("pages", com.jewellery360.service.PermissionService.PAGES);
        out.put("roles", java.util.Arrays.stream(com.jewellery360.domain.AppRole.values()).map(Enum::name).filter(r -> !"APP_ADMIN".equals(r)).toList());
        return out;
    }

    @PutMapping("/menu")
    @Transactional
    public Map<String, Object> saveMenu(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long companyId,
                                        @RequestBody com.fasterxml.jackson.databind.JsonNode body) throws Exception {
        requireSettings(me);
        Company company = resolveCompany(me, companyId);
        com.fasterxml.jackson.databind.JsonNode items = body.path("items");
        if (!items.isArray() || items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The menu needs at least one page.");
        java.util.Set<String> roles = java.util.EnumSet.allOf(com.jewellery360.domain.AppRole.class).stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<Map<String, Object>> clean = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode it : items) {
            String key = it.path("key").asText("");
            if (!com.jewellery360.service.PermissionService.PAGES.contains(key) || !seen.add(key))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or repeated page in the menu: " + key);
            String group = clean(it.path("group").asText(""));
            if (group.length() > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group names can be up to 30 characters.");
            String label = clean(it.path("label").asText(""));
            if (label.length() > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menu names can be up to 30 characters.");
            java.util.List<String> itemRoles = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode r : it.path("roles")) {
                if (!roles.contains(r.asText())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role " + r.asText());
                itemRoles.add(r.asText());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("label", label);
            m.put("group", group);
            m.put("hidden", it.path("hidden").asBoolean(false));
            m.put("roles", itemRoles);
            clean.add(m);
        }
        if ("SETTINGS".equals(clean.stream().filter(m -> Boolean.TRUE.equals(m.get("hidden"))).map(m -> m.get("key")).filter("SETTINGS"::equals).findFirst().orElse(null)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settings can't be hidden, or you could not change the menu back.");
        String json = JSON.writeValueAsString(clean);
        if (json.length() > 4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The menu layout is too large.");
        companyProperties.setMenuLayout(company, json);
        return getMenu(me, companyId);
    }

    @DeleteMapping("/menu")
    @Transactional
    public Map<String, Object> resetMenu(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long companyId) throws Exception {
        requireSettings(me);
        companyProperties.setMenuLayout(resolveCompany(me, companyId), null);
        return getMenu(me, companyId);
    }

    /* ---------------------------------------------------------------- dashboard layout (per user) */

    private static final String DASHBOARD_LAYOUT = "DASHBOARD_LAYOUT";
    private static final java.util.regex.Pattern WIDGET_ID = java.util.regex.Pattern.compile("[a-z][a-z0-9-]{0,39}");

    /** The signed-in user's own dashboard arrangement in this company: order and hidden widgets. Only layout, no figures. */
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long companyId) throws Exception {
        Company company = resolveCompany(me, companyId);
        String json = properties.findByCompanyIdAndUserIdAndKeyAndActiveTrue(company.getId(), me.getUserId(), DASHBOARD_LAYOUT).map(Property::getValue).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("custom", json != null);
        out.put("items", json == null ? java.util.List.of() : JSON.readValue(json, java.util.List.class));
        return out;
    }

    @PutMapping("/dashboard")
    @Transactional
    public Map<String, Object> saveDashboard(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long companyId,
                                             @RequestBody com.fasterxml.jackson.databind.JsonNode body) throws Exception {
        Company company = resolveCompany(me, companyId);
        com.fasterxml.jackson.databind.JsonNode items = body.path("items");
        if (!items.isArray() || items.isEmpty() || items.size() > 40) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The dashboard layout must list 1-40 widgets.");
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<Map<String, Object>> clean = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode it : items) {
            String id = it.path("id").asText("");
            if (!WIDGET_ID.matcher(id).matches() || !seen.add(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or repeated widget: " + id);
            clean.add(Map.of("id", id, "hidden", it.path("hidden").asBoolean(false)));
        }
        Property p = properties.findByCompanyIdAndUserIdAndKeyAndActiveTrue(company.getId(), me.getUserId(), DASHBOARD_LAYOUT).orElseGet(() -> {
            Property n = new Property();
            n.setCompany(company);
            n.setUser(users.getReferenceById(me.getUserId()));
            n.setKey(DASHBOARD_LAYOUT);
            n.setType("JSON");
            return n;
        });
        p.setValue(JSON.writeValueAsString(clean));
        properties.save(p);
        return getDashboard(me, companyId);
    }

    @DeleteMapping("/dashboard")
    @Transactional
    public Map<String, Object> resetDashboard(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long companyId) throws Exception {
        Company company = resolveCompany(me, companyId);
        properties.findByCompanyIdAndUserIdAndKeyAndActiveTrue(company.getId(), me.getUserId(), DASHBOARD_LAYOUT).ifPresent(properties::delete);
        return getDashboard(me, companyId);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private static java.util.List<Map<String, Object>> defaultMenu() {
        String[][] d = {{"OVERVIEW", "Daily"}, {"BILLING", "Daily"}, {"CUSTOMERS", "Daily"}, {"GOLD_RATES", "Daily"}, {"STOCK", "Stock"},
                {"OLD_GOLD", "Stock"}, {"PURCHASES", "Stock"}, {"SERVICES", "Work"}, {"MONEY", "Accounts"}, {"REPORTS", "Accounts"},
                {"AUDIT_LOGS", "Admin"}, {"SETTINGS", "Admin"}};
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (String[] x : d) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", x[0]); m.put("label", ""); m.put("group", x[1]); m.put("hidden", false); m.put("roles", java.util.List.of());
            out.add(m);
        }
        return out;
    }

    private void requireSettings(AuthenticatedUser me) {
        if (!"APP_ADMIN".equals(me.getRole()) && !"COMPANY_ADMIN".equals(me.getRole()) && !permissions.canEditSettings(me))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to change settings.");
    }

    private Company resolveCompany(AuthenticatedUser me, Long requestedCompanyId) {
        if ("APP_ADMIN".equals(me.getRole())) {
            Long id = requestedCompanyId;
            if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a company first.");
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

    private Map<String, Object> taxResponse(Company company, CompanyPropertyService.TaxSettings tax) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", company.getId());
        result.put("companyName", company.getName());
        result.put("enabled", tax.enabled());
        result.put("mode", tax.mode());
        result.put("rate", tax.rate());
        result.put("cgstRate", tax.cgstRate());
        result.put("sgstRate", tax.sgstRate());
        result.put("totalRate", tax.totalRate());
        return result;
    }

    private Map<String, Object> businessResponse(Company company, CompanyPropertyService.BusinessProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", company.getId());
        result.put("companyName", profile.companyName());
        result.put("phone", profile.phone());
        result.put("email", profile.email());
        result.put("gstin", profile.gstin());
        result.put("printFormat", profile.printFormat());
        result.put("skuPrefix", profile.skuPrefix());
        result.put("invoiceTemplate", profile.invoiceTemplate());
        return result;
    }

    private java.math.BigDecimal safeRate(java.math.BigDecimal value, String label) {
        java.math.BigDecimal n = value == null ? java.math.BigDecimal.ZERO : value;
        if (n.signum() < 0 || n.compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be between 0 and 100");
        }
        return n.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    public record BusinessRequest(String companyName, String phone, String email, String gstin, String printFormat, String skuPrefix, String invoiceTemplate) {}

    public record TaxRequest(boolean enabled, String mode, java.math.BigDecimal rate, java.math.BigDecimal cgstRate, java.math.BigDecimal sgstRate) {}

    public record WhatsAppRequest(
            boolean enabled,
            String accessToken,
            String phoneNumberId,
            String graphApiVersion,
            String webhookVerifyToken,
            String invoiceTemplateName,
            String templateLanguage) {}
}
