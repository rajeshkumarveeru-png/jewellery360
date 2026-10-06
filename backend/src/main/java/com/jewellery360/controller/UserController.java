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

import java.time.Instant;
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
        // Every new user of a company needs an approved request from the app administrator (no fixed number of users any more).
        boolean appAdmin = "APP_ADMIN".equals(me.getRole());
        UserRequest approval = null;
        if (!appAdmin) {
            approval = r.requestId() != null
                    ? requests.findById(r.requestId())
                            .filter(x -> x.getCompany() != null && Objects.equals(cid, x.getCompany().getId())
                                    && "USER_SLOT".equals(x.getRequestType()) && "APPROVED".equals(x.getStatus()))
                            .orElse(null)
                    : requests.findFirstByCompanyIdAndRequestTypeAndStatusOrderByIdAsc(cid, "USER_SLOT", "APPROVED").orElse(null);
            if (approval == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Ask the app administrator to approve a new user first (Users page - Request a new user).");
            }
        }
        Company c = companies.findById(cid).orElseThrow();
        Branch b = branches.findById(bid).orElseThrow();
        if (!Objects.equals(b.getCompany().getId(), cid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch does not belong to company");
        String username = r.username() == null ? "" : r.username().trim();
        String email = r.email() == null ? "" : r.email().trim().toLowerCase(Locale.ROOT);
        String phone = r.phone() == null ? "" : r.phone().replaceAll("[^0-9]", "");
        if (!username.matches("[A-Za-z0-9._-]{3,80}") || !username.matches(".*[A-Za-z].*"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be 3-80 characters, contain a letter, and use only letters, numbers, dot, underscore or hyphen.");
        if (!email.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        if (!phone.isBlank() && !phone.matches("[0-9]{10,15}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid phone number with 10-15 digits.");
        if (r.password() == null || r.password().length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters.");
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
        u.setEnabled(true); // the approval already happened (the app administrator approved the request)
        if (r.permissions() != null) u.setPermissions(PermissionService.serialize(r.permissions()));
        users.save(u);
        if (approval != null) {
            approval.setStatus("USED");
            approval.setCreatedUserId(u.getId());
            requests.save(approval);
        }
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

    /* ---------------- requests for new users (company admin -> app admin) ---------------- */

    private static final int MAX_PENDING_SLOT_REQUESTS = 5;

    private Map<String, Object> slotResponse(UserRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("requestedName", r.getRequestedName());
        m.put("note", r.getMessage());
        m.put("status", r.getStatus());
        m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        m.put("decidedAt", r.getProcessedAt() == null ? null : r.getProcessedAt().toString());
        m.put("decisionNote", r.getDecisionNote());
        m.put("createdUserId", r.getCreatedUserId());
        return m;
    }

    private void requireAdmin(AuthenticatedUser me) {
        if (!"APP_ADMIN".equals(me.getRole()) && !"COMPANY_ADMIN".equals(me.getRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can manage users");
    }

    @GetMapping("/slot-requests")
    public List<Map<String, Object>> slotRequests(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany) {
        requireAdmin(me);
        Long cid = resolveCompany(me, contextCompany);
        return requests.findByCompanyIdAndRequestTypeOrderByIdDesc(cid, "USER_SLOT").stream().limit(50).map(this::slotResponse).toList();
    }

    @PostMapping("/slot-requests")
    @Transactional
    public Map<String, Object> requestSlot(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody SlotRequestBody body,
                                           @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany) {
        if (!"COMPANY_ADMIN".equals(me.getRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the company administrator requests new users (the app administrator creates users directly).");
        Long cid = resolveCompany(me, contextCompany);
        String name = body.requestedName() == null ? "" : body.requestedName().trim();
        String note = body.note() == null ? "" : body.note().trim();
        if (name.length() < 2)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tell the administrator who the new user is for (at least 2 characters).");
        if (name.length() > 80) name = name.substring(0, 80);
        if (note.length() > 300) note = note.substring(0, 300);
        if (requests.countByCompanyIdAndRequestTypeAndStatus(cid, "USER_SLOT", "PENDING") >= MAX_PENDING_SLOT_REQUESTS)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have " + MAX_PENDING_SLOT_REQUESTS + " requests waiting for the administrator.");
        UserRequest req = new UserRequest();
        req.setRequestType("USER_SLOT");
        req.setStatus("PENDING");
        req.setCompany(companies.findById(cid).orElseThrow());
        req.setRequestedBy(users.findById(me.getUserId()).orElse(null));
        req.setRequestedName(name);
        req.setMessage(note.isBlank() ? null : note);
        requests.save(req);
        audit.log(me, "CREATE", "USER_REQUEST", req.getId(), null, slotResponse(req));
        return slotResponse(req);
    }

    @DeleteMapping("/slot-requests/{requestId}")
    @Transactional
    public Map<String, Object> cancelSlot(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long requestId,
                                          @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany) {
        requireAdmin(me);
        Long cid = resolveCompany(me, contextCompany);
        UserRequest req = requests.findById(requestId)
                .filter(x -> "USER_SLOT".equals(x.getRequestType()) && x.getCompany() != null && Objects.equals(cid, x.getCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        if (!"PENDING".equals(req.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a request that is still waiting can be cancelled.");
        req.setStatus("CANCELLED");
        req.setProcessedAt(Instant.now());
        requests.save(req);
        return slotResponse(req);
    }

    /* ---------------- access + activation ---------------- */

    private AppUser managedUser(AuthenticatedUser me, Long id, Long contextCompany) {
        requireAdmin(me);
        AppUser u = users.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!"APP_ADMIN".equals(me.getRole())
                && !Objects.equals(resolveCompany(me, contextCompany), u.getCompany() == null ? null : u.getCompany().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User access denied");
        return u;
    }

    /** The company admin chooses which pages and powers a user has. Applies immediately; send a shorter list to take access away. */
    @PutMapping("/{id}/permissions")
    @Transactional
    public Map<String, Object> setPermissions(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody PermissionsBody body,
                                              @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany) {
        AppUser u = managedUser(me, id, contextCompany);
        if (u.getRole() == AppRole.APP_ADMIN || u.getRole() == AppRole.COMPANY_ADMIN)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrators always have full access.");
        u.setPermissions(PermissionService.serialize(body.permissions()));
        users.save(u);
        audit.log(me, "UPDATE", "USER_ACCESS", id, null, auth.userResponse(u));
        return auth.userResponse(u);
    }

    @PostMapping("/{id}/disable")
    @Transactional
    public Map<String, Object> disable(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                                       @RequestHeader(value = "X-Company-Id", required = false) Long contextCompany) {
        AppUser u = managedUser(me, id, contextCompany);
        if (u.getRole() == AppRole.APP_ADMIN || u.getRole() == AppRole.COMPANY_ADMIN)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An administrator account cannot be deactivated here.");
        u.setEnabled(false);
        users.save(u);
        audit.log(me, "DISABLE", "USER", id, null, auth.userResponse(u));
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
                                    Long branchId, java.util.List<String> permissions, Long requestId) {
    }

    public record SlotRequestBody(String requestedName, String note) {
    }

    public record PermissionsBody(java.util.List<String> permissions) {
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
