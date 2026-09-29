package com.jewellery360.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.AppUser;
import com.jewellery360.domain.Company;
import com.jewellery360.domain.Property;
import com.jewellery360.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserPropertyService {
    public static final String MENU_PREFERENCES = "UI_MENU_PREFERENCES";

    private final PropertyRepository properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<String> getMenuPreference(Long companyId, Long userId) {
        if (companyId == null || userId == null) return List.of();
        return properties.findByCompanyIdAndUserIdAndKeyAndActiveTrue(companyId, userId, MENU_PREFERENCES)
                .map(Property::getValue)
                .map(this::readMenu)
                .orElseGet(List::of);
    }

    @Transactional
    public List<String> saveMenuPreference(Company company, AppUser user, List<String> menu, String role) {
        if (company == null || company.getId() == null) throw new IllegalArgumentException("Company is required");
        if (user == null || user.getId() == null) throw new IllegalArgumentException("User is required");
        if (user.getCompany() != null && !company.getId().equals(user.getCompany().getId())) {
            throw new IllegalArgumentException("User does not belong to the selected company");
        }

        List<String> normalized = normalizeMenu(menu, role);
        try {
            Property p = properties.findByCompanyIdAndUserIdAndKeyAndActiveTrue(company.getId(), user.getId(), MENU_PREFERENCES)
                    .orElseGet(Property::new);
            p.setCompany(company);
            p.setUser(user);
            p.setKey(MENU_PREFERENCES);
            p.setValue(objectMapper.writeValueAsString(normalized));
            p.setType("JSON");
            p.setActive(true);
            properties.save(p);
            return normalized;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to store menu preferences", e);
        }
    }

    @Transactional
    public List<String> initializeDefaultMenuPreference(Company company, AppUser user) {
        if (company == null || company.getId() == null) {
            throw new IllegalArgumentException("Company is required");
        }
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required");
        }
        if (user.getCompany() != null && !company.getId().equals(user.getCompany().getId())) {
            throw new IllegalArgumentException("User does not belong to the selected company");
        }

        Property existing = properties.findByCompanyIdAndUserIdAndKeyAndActiveTrue(
                company.getId(), user.getId(), MENU_PREFERENCES).orElse(null);
        if (existing != null) {
            return readMenu(existing.getValue());
        }

        List<String> defaults = defaultMenu(user.getRole() == null ? null : user.getRole().name());
        try {
            Property p = new Property();
            p.setCompany(company);
            p.setUser(user);
            p.setKey(MENU_PREFERENCES);
            p.setValue(objectMapper.writeValueAsString(defaults));
            p.setType("JSON");
            p.setActive(true);
            properties.save(p);
            return defaults;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to initialize default menu preferences", e);
        }
    }

    private List<String> normalizeMenu(List<String> menu, String role) {
        List<String> defaults = defaultMenu(role);
        Set<String> allowed = new LinkedHashSet<>(defaults);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (menu != null) {
            menu.stream()
                    .map(x -> x == null ? "" : x.trim())
                    .filter(allowed::contains)
                    .forEach(ordered::add);
        }
        if (ordered.isEmpty()) {
            return defaults;
        }
        return new ArrayList<>(ordered);
    }

    private List<String> defaultMenu(String role) {
        return switch (role == null ? "" : role) {
            case "APP_ADMIN" -> List.of("Overview","Companies","Branches","Users","Approvals","Billing","Jewellery","Gold & Rates","Inventory","Purchases","Old Gold","Customers","Services","Payments","Reports","WhatsApp","Settings","Audit Logs");
            case "COMPANY_ADMIN" -> List.of("Overview","Branches","Users","Billing","Jewellery","Gold & Rates","Inventory","Purchases","Old Gold","Customers","Services","Payments","Reports","WhatsApp","Settings","Audit Logs");
            case "MANAGER" -> List.of("Overview","Billing","Jewellery","Gold & Rates","Inventory","Purchases","Old Gold","Customers","Services","Payments","Reports","WhatsApp","Audit Logs");
            case "CASHIER" -> List.of("Overview","Billing","Customers","Payments","Reports");
            case "SALESMAN" -> List.of("Overview","Billing","Customers","Services","Payments");
            case "INVENTORY_MANAGER" -> List.of("Overview","Jewellery","Gold & Rates","Inventory","Purchases","Old Gold","Reports");
            case "ACCOUNTANT" -> List.of("Overview","Customers","Payments","Reports");
            case "VIEWER" -> List.of("Overview","Customers","Inventory","Reports");
            default -> List.of("Overview");
        };
    }

    private List<String> readMenu(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
