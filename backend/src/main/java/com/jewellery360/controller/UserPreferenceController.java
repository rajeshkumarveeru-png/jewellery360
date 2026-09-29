package com.jewellery360.controller;

import com.jewellery360.domain.AppUser;
import com.jewellery360.domain.Company;
import com.jewellery360.repository.AppUserRepository;
import com.jewellery360.repository.CompanyRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.UserPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/properties/user")
@RequiredArgsConstructor
public class UserPreferenceController {
    private final UserPropertyService userProperties;
    private final AppUserRepository users;
    private final CompanyRepository companies;

    @GetMapping("/menu")
    public Map<String, Object> getMenu(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long companyIdParam) {
        Long companyId = resolveCompany(me, companyIdParam);
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required for user menu preferences.");
        }
        return Map.of("companyId", companyId, "userId", me.getUserId(), "menu", userProperties.getMenuPreference(companyId, me.getUserId()));
    }

    @PutMapping("/menu")
    public Map<String, Object> saveMenu(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long companyIdParam, @RequestBody MenuRequest request) {
        Long companyId = resolveCompany(me, companyIdParam);
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required for user menu preferences.");
        }
        AppUser user = users.findById(me.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        List<String> menu = request == null || request.menu() == null ? List.of() : request.menu();
        return Map.of("companyId", companyId, "userId", me.getUserId(), "menu", userProperties.saveMenuPreference(company, user, menu, me.getRole()));
    }

    private Long resolveCompany(AuthenticatedUser me, Long requestedCompanyId) {
        if ("APP_ADMIN".equals(me.getRole())) {
            if (requestedCompanyId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required for user menu preferences.");
            return requestedCompanyId;
        }
        if (me.getCompanyId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required for user menu preferences.");
        if (requestedCompanyId != null && !me.getCompanyId().equals(requestedCompanyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot edit another company preference");
        }
        return me.getCompanyId();
    }

    public record MenuRequest(List<String> menu) {}
}
