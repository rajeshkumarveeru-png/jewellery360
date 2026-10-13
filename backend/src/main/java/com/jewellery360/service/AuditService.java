package com.jewellery360.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.AuditHistory;
import com.jewellery360.repository.AppUserRepository;
import com.jewellery360.repository.AuditHistoryRepository;
import com.jewellery360.repository.BranchRepository;
import com.jewellery360.repository.CompanyRepository;
import com.jewellery360.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who did what, when, to which record - with before / after values where they matter. */
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditHistoryRepository audits;
    private final AppUserRepository users;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final ObjectMapper mapper;
    private final HttpServletRequest request;

    @Transactional
    public void log(TenantContext.Ctx ctx, String action, String entity, Long id, Object before, Object after) {
        save(ctx.me(), ctx.companyId(), ctx.branchId(), action, entity, id, before, after);
    }

    /** For callers without a resolved tenant context (user administration, sign-in). */
    @Transactional
    public void log(AuthenticatedUser me, String action, String entity, Long id, Object before, Object after) {
        Long cid = me != null ? me.getCompanyId() : null;
        Long bid = me != null ? me.getBranchId() : null;
        if (me != null && "APP_ADMIN".equals(me.getRole())) {
            cid = header("X-Company-Id");
            bid = header("X-Branch-Id");
        }
        save(me, cid, bid, action, entity, id, before, after);
    }

    private void save(AuthenticatedUser me, Long cid, Long bid, String action, String entity, Long id, Object before, Object after) {
        AuditHistory a = new AuditHistory();
        a.setUser(me == null ? null : users.getReferenceById(me.getUserId()));
        a.setCompany(cid == null ? null : companies.getReferenceById(cid));
        a.setBranch(bid == null ? null : branches.getReferenceById(bid));
        a.setAction(action);
        a.setEntityType(entity);
        a.setEntityId(id);
        try {
            a.setBeforeValue(before == null ? null : mapper.writeValueAsString(before));
            a.setAfterValue(after == null ? null : mapper.writeValueAsString(after));
        } catch (Exception ignored) {
            // the event itself is still recorded
        }
        audits.save(a);
    }

    private Long header(String name) {
        try { String v = request.getHeader(name); return v == null || v.isBlank() ? null : Long.valueOf(v); } catch (Exception e) { return null; }
    }
}
