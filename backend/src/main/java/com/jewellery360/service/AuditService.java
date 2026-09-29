package com.jewellery360.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    public void log(AuthenticatedUser me, String action, String entity, Long id, Object before, Object after) {
        AuditHistory a = new AuditHistory();
        a.setUser(me == null ? null : users.findById(me.getUserId()).orElse(null));
        Long cid = me != null ? me.getCompanyId() : null;
        Long bid = me != null ? me.getBranchId() : null;
        if (me != null && "APP_ADMIN".equals(me.getRole())) {
            try {
                cid = Long.valueOf(request.getHeader("X-Company-Id"));
                bid = Long.valueOf(request.getHeader("X-Branch-Id"));
            } catch (Exception ignored) {
            }
        }
        a.setCompany(cid != null ? companies.findById(cid).orElse(null) : null);
        a.setBranch(bid != null ? branches.findById(bid).orElse(null) : null);
        a.setAction(action);
        a.setEntityType(entity);
        a.setEntityId(id);
        try {
            a.setBeforeValue(before == null ? null : mapper.writeValueAsString(before));
            a.setAfterValue(after == null ? null : mapper.writeValueAsString(after));
        } catch (Exception ignored) {
        }
        audits.save(a);
    }
}
