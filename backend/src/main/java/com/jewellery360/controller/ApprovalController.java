package com.jewellery360.controller;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.AuditService;
import com.jewellery360.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/approvals")
@Slf4j
@RequiredArgsConstructor
public class ApprovalController {
    private final UserRequestRepository requests;
    private final AppUserRepository users;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final AuditService audit;
    private final WhatsAppService whatsApp;
    private final com.jewellery360.service.CompanyPropertyService companyProperties;
    private final com.jewellery360.service.UserPropertyService userProperties;

    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public List<ApprovalResponse> pending(@AuthenticationPrincipal AuthenticatedUser me) {
        if ("APP_ADMIN".equals(me.getRole())) {
            return requests.findByStatusOrderByCreatedAtDesc("PENDING")
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        if (me.getCompanyId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Company context required"
            );
        }

        return requests.findByCompanyIdAndStatusOrderByCreatedAtDesc(
                        me.getCompanyId(),
                        "PENDING"
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public Map<String, Object> approve(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long id
    ) {
        UserRequest r = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Approval request not found"
                ));

        if (!"PENDING".equalsIgnoreCase(r.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This approval request has already been processed."
            );
        }

        boolean appAdmin = "APP_ADMIN".equals(me.getRole());

        // Company registration is a platform-level operation.
        if ("NEW_ACCOUNT".equals(r.getRequestType()) && !appAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only APP_ADMIN can approve company registrations"
            );
        }

        if (!appAdmin) {
            if (r.getCompany() == null
                    || !Objects.equals(me.getCompanyId(), r.getCompany().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Request belongs to another company"
                );
            }
        }

        if ("PASSWORD_RESET".equals(r.getRequestType())) {
            if (r.getTargetUser() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Approval request has no target user."
                );
            }

            if (r.getTargetUser().getRole() == AppRole.COMPANY_ADMIN && !appAdmin) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Company Admin reset requires App Admin approval"
                );
            }

            if (r.getPendingPasswordHash() != null) {
                r.getTargetUser().setPasswordHash(r.getPendingPasswordHash());
                users.save(r.getTargetUser());
            }
        } else if ("NEW_ACCOUNT".equals(r.getRequestType())) {
            // APP_ADMIN approval activates all three parts of the registration:
            // Company + Branch + Company Admin user.
            if (r.getTargetUser() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Registration request has no target user."
                );
            }

            r.getTargetUser().setEnabled(true);
            users.save(r.getTargetUser());

            if (r.getCompany() != null) {
                r.getCompany().setActive(true);
                companies.save(r.getCompany());
                // Create the complete company-scoped WhatsApp property set at approval time.
                // Secrets intentionally start blank and must be supplied by an administrator.
                companyProperties.initializeWhatsAppDefaults(r.getCompany());
                companyProperties.initializeTaxDefaults(r.getCompany());
                // Every approved Company Admin starts with a DB-backed menu preference.
                userProperties.initializeDefaultMenuPreference(r.getCompany(), r.getTargetUser());
            }

            if (r.getTargetUser().getBranch() != null) {
                r.getTargetUser().getBranch().setActive(true);
                branches.save(r.getTargetUser().getBranch());
            }
        } else if ("NEW_USER".equals(r.getRequestType())) {
            if (r.getTargetUser() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "User request has no target user."
                );
            }

            r.getTargetUser().setEnabled(true);
            users.save(r.getTargetUser());
            if (r.getTargetUser().getCompany() != null) {
                // Every approved user starts with a DB-backed role-specific menu preference.
                userProperties.initializeDefaultMenuPreference(r.getTargetUser().getCompany(), r.getTargetUser());
            }
        }

        r.setStatus("APPROVED");
        r.setProcessedAt(Instant.now());

        // Persist the approval before attempting the optional WhatsApp notification.
        // A WhatsApp failure must never roll back a valid administrator approval.
        requests.save(r);

        audit.log(
                me,
                "APPROVE",
                "USER_REQUEST",
                r.getId(),
                null,
                Map.of(
                        "type", r.getRequestType(),
                        "status", "APPROVED",
                        "targetUserId", r.getTargetUser() == null ? null : r.getTargetUser().getId()
                )
        );

        AppUser target = r.getTargetUser();
        boolean notificationSent = false;
        String notificationMessage;

        if (target == null) {
            notificationMessage = "WhatsApp notification skipped because the request has no target user.";
        } else if (target.getPhone() == null || target.getPhone().isBlank()) {
            notificationMessage = "WhatsApp notification skipped because the user has no WhatsApp phone number.";
        } else if (target.getCompany() == null) {
            notificationMessage = "WhatsApp notification skipped because the user has no company.";
        } else {
            try {
                String companyName = target.getCompany().getName();
                if (companyName == null || companyName.isBlank()) companyName = "Jewellery360";

                String message;
                if ("PASSWORD_RESET".equalsIgnoreCase(r.getRequestType())) {
                    message = """
                            Jewellery360 Password Reset Successful

                            Hello %s,

                            Your password reset request has been approved by the administrator.
                            Your new password is now active and you can sign in to Jewellery360.

                            Thank you,
                            Jewellery360 Team
                            """.formatted(target.getUsername());
                } else {
                    message = """
                            🎉 Jewellery360 account activated!

                            Hello %s,

                            Your %s account has been approved and activated.
                            You can now sign in and start using Jewellery360.

                            Thank you,
                            Jewellery360 Team
                            """.formatted(target.getUsername(), companyName);
                }

                whatsApp.sendText(target.getCompany().getId(), target.getPhone(), message);
                notificationSent = true;
                notificationMessage = "WhatsApp notification sent successfully.";
            } catch (Exception ex) {
                notificationMessage = "WhatsApp notification failed. Approval was completed successfully.";
                log.error(
                        "WhatsApp approval notification failed. requestId={}, requestType={}, targetUserId={}, companyId={}",
                        r.getId(),
                        r.getRequestType(),
                        target.getId(),
                        target.getCompany().getId(),
                        ex
                );
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Request approved. The account is now active and can sign in.");
        response.put("requestId", r.getId());
        response.put("requestType", r.getRequestType());
        response.put("notificationSent", notificationSent);
        response.put("notificationMessage", notificationMessage);
        return response;
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public Map<String, Object> reject(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long id
    ) {
        UserRequest r = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Approval request not found"
                ));

        if (!"PENDING".equalsIgnoreCase(r.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This approval request has already been processed."
            );
        }

        boolean appAdmin = "APP_ADMIN".equals(me.getRole());

        if ("NEW_ACCOUNT".equals(r.getRequestType()) && !appAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only APP_ADMIN can reject company registrations"
            );
        }

        if (!appAdmin && (r.getCompany() == null
                || !Objects.equals(me.getCompanyId(), r.getCompany().getId()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Request belongs to another company"
            );
        }

        r.setStatus("REJECTED");
        r.setProcessedAt(Instant.now());
        requests.save(r);

        audit.log(
                me,
                "REJECT",
                "USER_REQUEST",
                r.getId(),
                null,
                Map.of(
                        "type", r.getRequestType(),
                        "status", "REJECTED"
                )
        );

        return Map.of(
                "message",
                "Request rejected.",
                "requestId",
                r.getId()
        );
    }

    private ApprovalResponse toResponse(UserRequest r) {
        AppUser target = r.getTargetUser();
        Company company = r.getCompany();
        Branch branch = target == null ? null : target.getBranch();

        return new ApprovalResponse(
                r.getId(),
                r.getRequestType(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getProcessedAt(),
                r.getMessage(),
                target == null ? null : target.getId(),
                target == null ? null : target.getUsername(),
                target == null ? null : target.getEmail(),
                target == null ? null : target.getPhone(),
                target == null ? null : target.getRole().name(),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName()
        );
    }

    public record ApprovalResponse(
            Long id,
            String requestType,
            String status,
            Instant createdAt,
            Instant processedAt,
            String message,
            Long targetUserId,
            String username,
            String email,
            String phone,
            String role,
            Long companyId,
            String companyName,
            Long branchId,
            String branchName
    ) {}
}
