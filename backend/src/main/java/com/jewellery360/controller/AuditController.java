package com.jewellery360.controller;

import com.jewellery360.domain.AuditHistory;
import com.jewellery360.repository.AuditHistoryRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

  private final AuditHistoryRepository audits;
  private final PermissionService permissions;

  @GetMapping
  public List<Map<String, Object>> list(
          @AuthenticationPrincipal AuthenticatedUser me,
          @RequestHeader(value = "X-Company-Id", required = false) Long companyId) {

    List<AuditHistory> auditHistory;

    if ("APP_ADMIN".equals(me.getRole()) && companyId == null) {

      auditHistory = audits.findTop200ByOrderByCreatedAtDesc();

    } else {

      Long cid = "APP_ADMIN".equals(me.getRole())
              ? companyId
              : me.getCompanyId();

      auditHistory =
              audits.findTop200ByCompanyIdOrderByCreatedAtDesc(cid);
    }

    return auditHistory.stream()
            .map(this::toResponse)
            .toList();
  }

  /**
   * Convert AuditHistory entity to a plain API response.
   *
   * No JPA entities are exposed to Jackson.
   */
  private Map<String, Object> toResponse(AuditHistory audit) {

    Map<String, Object> response = new LinkedHashMap<>();

    response.put("id", audit.getId());
    response.put("createdAt", audit.getCreatedAt());
    response.put("action", audit.getAction());
    response.put("entityType", audit.getEntityType());
    response.put(
            "entityId",
            audit.getEntityId() == null ? "" : audit.getEntityId()
    );

    response.put(
            "user",
            audit.getUser() == null
                    ? ""
                    : audit.getUser().getUsername()
    );

    response.put(
            "company",
            audit.getCompany() == null
                    ? ""
                    : audit.getCompany().getName()
    );

    response.put(
            "branch",
            audit.getBranch() == null
                    ? ""
                    : audit.getBranch().getName()
    );

    return response;
  }
}