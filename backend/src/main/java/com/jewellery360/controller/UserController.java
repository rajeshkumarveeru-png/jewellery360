package com.jewellery360.controller;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserController {
    private final AppUserRepository users;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final PasswordEncoder encoder;
    private final UserRequestRepository requests;
    private final AuthService auth;
    private final PermissionService permissions;
    private final AuditService audit;

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser me) {
        return users.findById(me.getUserId()).map(auth::userResponse).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany) {
        String q = search == null ? "" : search.trim();
        if ("APP_ADMIN".equals(me.getRole())) {
            List<AppUser> result = q.isBlank()
                    ? users.findAllByDeletedFalseOrderByUsernameAsc()
                    : users.searchAllActive(q);
            return result.stream().map(auth::userResponse).toList();
        }
        Long cid = resolveCompany(me, contextCompany);
        List<AppUser> result = q.isBlank()
                ? users.findByCompanyIdAndDeletedFalse(cid)
                : users.searchCompanyActive(cid, q);
        return result.stream().map(auth::userResponse).toList();
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody CreateUserRequest r,
                                      @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany, @RequestHeader(value = "X-Branch-Id", required = false) Long contextBranch) {
        if (!"APP_ADMIN".equals(me.getRole()) && !"COMPANY_ADMIN".equals(me.getRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can create users");
        Long cid = resolveCompany(me, contextCompany);
        Long bid = contextBranch != null ? contextBranch : me.getBranchId();
        if (bid == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch context is required");
        long count = users.countByCompanyIdAndRoleNotAndDeletedFalse(cid, AppRole.COMPANY_ADMIN);
        if (count >= 10) throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum 10 company users allowed");
        Company c = companies.findById(cid).orElseThrow();
        Branch b = branches.findById(bid).orElseThrow();
        if (!Objects.equals(b.getCompany().getId(), cid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch does not belong to company");
        String username = r.username() == null ? "" : r.username().trim();
        String email = r.email() == null ? "" : r.email().trim().toLowerCase(Locale.ROOT);
        String phone = r.phone() == null ? "" : r.phone().replaceAll("[^0-9]", "");
        if (users.existsByUsernameIgnoreCaseAndDeletedFalse(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use across Jewellery360");
        if (users.existsByEmailIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        if (!phone.isBlank() && users.existsByPhone(phone))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already in use");
        AppRole role = AppRole.valueOf(r.role());
        if (role == AppRole.APP_ADMIN || role == AppRole.COMPANY_ADMIN)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use platform/company administration for administrator accounts");
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(role);
        u.setCompany(c);
        u.setBranch(b);
        u.setEnabled(false);
        users.save(u);
        UserRequest req = new UserRequest();
        req.setRequestType("NEW_USER");
        req.setStatus("PENDING");
        req.setTargetUser(u);
        req.setRequestedBy(users.findById(me.getUserId()).orElse(null));
        req.setCompany(c);
        req.setMessage("New company user awaiting approval.");
        requests.save(req);
        audit.log(me, "CREATE", "USER", u.getId(), null, auth.userResponse(u));
        return auth.userResponse(u);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long id,
            @RequestBody UpdateUserRequest r,
            @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany) {

        AppUser u = users.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"));

        Long userCompanyId = u.getCompany() == null
                ? null
                : u.getCompany().getId();

        // ---------------------------------------------------------
        // Authorization
        // ---------------------------------------------------------
        if ("APP_ADMIN".equals(me.getRole())) {

            if (contextCompany != null &&
                    !Objects.equals(contextCompany, userCompanyId)) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User does not belong to selected company");
            }

        } else {

            if (!Objects.equals(me.getCompanyId(), userCompanyId)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User access denied");
            }

            if (!"COMPANY_ADMIN".equals(me.getRole())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only a company administrator can edit users");
            }
        }

        // ---------------------------------------------------------
        // Basic validation
        // ---------------------------------------------------------
        String username = r.username() == null
                ? ""
                : r.username().trim();

        String email = r.email() == null
                ? ""
                : r.email().trim().toLowerCase(Locale.ROOT);

        String phone = r.phone() == null
                ? ""
                : r.phone().replaceAll("[^0-9]", "");

        if (username.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username is required");
        }

        if (email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is required");
        }

        // ---------------------------------------------------------
        // Username uniqueness
        // ---------------------------------------------------------
        users.findByUsernameIgnoreCaseAndDeletedFalse(username)
                .ifPresent(existing -> {
                    if (!Objects.equals(existing.getId(), id)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Username is already in use across Jewellery360");
                    }
                });

        // ---------------------------------------------------------
        // Email uniqueness
        // ---------------------------------------------------------
        users.findByEmailIgnoreCaseAndDeletedFalse(email)
                .ifPresent(existing -> {
                    if (!Objects.equals(existing.getId(), id)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Email is already in use");
                    }
                });

        // ---------------------------------------------------------
        // Phone uniqueness
        // ---------------------------------------------------------
        if (!phone.isBlank()) {
            users.findByPhoneAndDeletedFalse(phone)
                    .ifPresent(existing -> {
                        if (!Objects.equals(existing.getId(), id)) {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Phone number is already in use");
                        }
                    });
        }

        // ---------------------------------------------------------
        // Role
        // ---------------------------------------------------------
        AppRole role;

        try {
            role = AppRole.valueOf(r.role());
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid role");
        }

        if (role == AppRole.APP_ADMIN ||
                role == AppRole.COMPANY_ADMIN) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use platform/company administration for administrator accounts");
        }

        // ---------------------------------------------------------
        // Branch
        // ---------------------------------------------------------
        if (r.branchId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Branch is required");
        }

        Branch branch = branches.findById(r.branchId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Branch not found"));

        if (branch.getCompany() == null ||
                !Objects.equals(
                        branch.getCompany().getId(),
                        userCompanyId)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Branch does not belong to user's company");
        }

        // ---------------------------------------------------------
        // Update
        // ---------------------------------------------------------
        u.setUsername(username);
        u.setEmail(email);
        u.setPhone(phone);
        u.setRole(role);
        u.setBranch(branch);

        // Password is optional
        if (r.password() != null &&
                !r.password().isBlank()) {

            u.setPasswordHash(
                    encoder.encode(r.password())
            );
        }

        users.save(u);

        // ---------------------------------------------------------
        // Audit
        // ---------------------------------------------------------
        audit.log(
                me,
                "UPDATE",
                "USER",
                u.getId(),
                null,
                auth.userResponse(u)
        );

        return auth.userResponse(u);
    }

    @PostMapping("/{id}/enable")
    @Transactional
    public Map<String, Object> enable(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany) {
        AppUser u = users.findById(id).orElseThrow();
        if (!"APP_ADMIN".equals(me.getRole()) && !Objects.equals(resolveCompany(me, contextCompany), u.getCompany() == null ? null : u.getCompany().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User access denied");
        if (!"APP_ADMIN".equals(me.getRole()) && !"COMPANY_ADMIN".equals(me.getRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        u.setEnabled(true);
        users.save(u);
        audit.log(me, "ENABLE", "USER", id, null, auth.userResponse(u));
        return auth.userResponse(u);
    }

    private Long resolveCompany(AuthenticatedUser me, Long ctx) {
        if ("APP_ADMIN".equals(me.getRole())) {
            if (ctx == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required");
            return ctx;
        }
        return me.getCompanyId();
    }

    public record CreateUserRequest(String username, String email, String phone, String password, String role,
                                    Long branchId) {
    }

    public record UpdateUserRequest(
            String username,
            String email,
            String phone,
            String password,
            String role,
            Long branchId
    ) {
    }
}
