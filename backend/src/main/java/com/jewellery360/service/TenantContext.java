package com.jewellery360.service;

import com.jewellery360.domain.AppUser;
import com.jewellery360.domain.Branch;
import com.jewellery360.domain.Company;
import com.jewellery360.repository.AppUserRepository;
import com.jewellery360.repository.BranchRepository;
import com.jewellery360.repository.CompanyRepository;
import com.jewellery360.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Works out which company and branch a request acts on - the tenant-isolation boundary used by every service.
 *
 *  - Staff: always their own company and branch; request headers are ignored.
 *  - COMPANY_ADMIN (owner): own company; may pick any branch of it with X-Branch-Id (default: own branch).
 *  - APP_ADMIN: must pick the company (X-Company-Id) and branch (X-Branch-Id).
 *
 * Every record loaded by id must then pass {@link #check} so one company can never read or change another's data.
 */
@Component
@RequiredArgsConstructor
public class TenantContext {
    private final HttpServletRequest request;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final AppUserRepository users;

    /** company is never null; branch is null only for a company admin with no branch looking at all branches. */
    public record Ctx(AuthenticatedUser me, Company company, Branch branch) {
        public Long companyId() { return company.getId(); }
        public Long branchId() { return branch == null ? null : branch.getId(); }
        public boolean admin() { return PermissionService.isAdmin(me.getRole()); }
    }

    /** Company scope; branch when one applies. */
    public Ctx of(AuthenticatedUser me) {
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        Long companyId = me.getCompanyId();
        Long branchId = me.getBranchId();
        if ("APP_ADMIN".equals(me.getRole())) {
            companyId = header("X-Company-Id");
            branchId = header("X-Branch-Id");
            if (companyId == null) throw bad("Select a company first.");
        } else if ("COMPANY_ADMIN".equals(me.getRole())) {
            Long picked = header("X-Branch-Id");
            if (picked != null) branchId = picked;
        }
        if (companyId == null) throw bad("Your account is not linked to a company.");
        Company company = companies.findById(companyId).orElseThrow(() -> bad("Company not found."));
        Branch branch = null;
        if (branchId != null) {
            branch = branches.findById(branchId).orElseThrow(() -> bad("Branch not found."));
            if (!Objects.equals(branch.getCompany().getId(), company.getId())) throw forbidden("That branch belongs to another company.");
        }
        return new Ctx(me, company, branch);
    }

    /** Company and branch scope - for anything that creates stock, money or bills. */
    public Ctx withBranch(AuthenticatedUser me) {
        Ctx ctx = of(me);
        if (ctx.branch() == null) throw bad("Select a branch first.");
        return ctx;
    }

    /** Ensures a record belongs to the caller's company (and to the caller's branch for branch-bound staff). */
    public void check(Ctx ctx, Company company, Branch branch) {
        if (company == null || !Objects.equals(company.getId(), ctx.companyId())) throw notFound();
        if (!ctx.admin() && branch != null && ctx.branch() != null && !Objects.equals(branch.getId(), ctx.branchId())) throw notFound();
    }

    /** Ensures a company-level record (customer, product, supplier...) belongs to the caller's company. */
    public void checkCompany(Ctx ctx, Company company) {
        if (company == null || !Objects.equals(company.getId(), ctx.companyId())) throw notFound();
    }

    public AppUser user(AuthenticatedUser me) {
        return me == null ? null : users.getReferenceById(me.getUserId());
    }

    private Long header(String name) {
        String v = request.getHeader(name);
        if (v == null || v.isBlank() || "ALL".equalsIgnoreCase(v)) return null;
        try { return Long.valueOf(v.trim()); } catch (NumberFormatException e) { throw bad("Invalid " + name); }
    }

    /** "Not found" rather than "forbidden": another company's ids are not even acknowledged. */
    public static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found."); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException forbidden(String m) { return new ResponseStatusException(HttpStatus.FORBIDDEN, m); }
}
