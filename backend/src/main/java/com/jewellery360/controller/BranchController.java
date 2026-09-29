package com.jewellery360.controller;

import com.jewellery360.domain.Branch;
import com.jewellery360.domain.Company;
import com.jewellery360.repository.BranchRepository;
import com.jewellery360.repository.CompanyRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.AuditService;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchRepository branches;
    private final CompanyRepository companies;
    private final PermissionService permissions;
    private final AuditService audit;

    /**
     * List branches.
     *
     * BranchRepository uses @EntityGraph(attributePaths = "company"),
     * so company is loaded together with each branch.
     */
    @GetMapping
    public List<Map<String, Object>> list(
            @AuthenticationPrincipal AuthenticatedUser me) {

        if ("APP_ADMIN".equals(me.getRole())) {
            return branches.findAll()
                    .stream()
                    .map(this::view)
                    .toList();
        }

        return branches.findByCompanyId(me.getCompanyId())
                .stream()
                .map(this::view)
                .toList();
    }

    /**
     * Create a branch.
     */
    @PostMapping
    public Map<String, Object> create(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestBody Request r) {

        Long cid = "APP_ADMIN".equals(me.getRole())
                ? r.companyId()
                : me.getCompanyId();

        if ("APP_ADMIN".equals(me.getRole())) {
            permissions.requireAppAdmin(me);
        } else if (!"COMPANY_ADMIN".equals(me.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin only"
            );
        }

        Company company = companies.findById(cid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Company not found"
                ));

        Branch branch = new Branch();
        branch.setCompany(company);
        branch.setName(r.name());
        branch.setCode(r.code());
        branch.setPhone(r.phone());
        branch.setEmail(r.email());
        branch.setGstin(r.gstin());
        branch.setWebsite(r.website());
        branch.setInvoiceTitle(r.invoiceTitle());
        branch.setInvoiceSubtitle(r.invoiceSubtitle());
        branch.setInvoiceTerms(r.invoiceTerms());
        branch.setInvoiceFooter(r.invoiceFooter());
        branch.setAddress(r.address());
        branch.setActive(true);

        Branch savedBranch = branches.save(branch);
        // save() may return the entity with a lazy company proxy; reload through the
        // EntityGraph-backed repository before serializing the response.
        savedBranch = branches.findById(savedBranch.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found after save"));

        Map<String, Object> response = view(savedBranch);

        audit.log(
                me,
                "CREATE",
                "BRANCH",
                savedBranch.getId(),
                null,
                response
        );

        return response;
    }

    /**
     * Update a branch.
     */
    @PutMapping("/{id}")
    public Map<String, Object> update(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long id,
            @RequestBody Request r) {

        Branch branch = branches.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Branch not found"
                ));

        /*
         * BranchRepository.findById() uses @EntityGraph(attributePaths = "company"),
         * therefore branch.getCompany() is already initialized here.
         */
        if (!"APP_ADMIN".equals(me.getRole())
                && !Objects.equals(
                me.getCompanyId(),
                branch.getCompany().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Branch access denied"
            );
        }

        if (!"APP_ADMIN".equals(me.getRole())
                && !"COMPANY_ADMIN".equals(me.getRole())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin only"
            );
        }

        Map<String, Object> before = view(branch);

        branch.setName(r.name());
        branch.setCode(r.code());
        branch.setPhone(r.phone());
        branch.setEmail(r.email());
        branch.setGstin(r.gstin());
        branch.setWebsite(r.website());
        branch.setInvoiceTitle(r.invoiceTitle());
        branch.setInvoiceSubtitle(r.invoiceSubtitle());
        branch.setInvoiceTerms(r.invoiceTerms());
        branch.setInvoiceFooter(r.invoiceFooter());
        branch.setAddress(r.address());

        Branch savedBranch = branches.save(branch);
        // Reload so company is initialized before view()/audit serialization.
        savedBranch = branches.findById(savedBranch.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found after update"));

        Map<String, Object> after = view(savedBranch);

        audit.log(
                me,
                "UPDATE",
                "BRANCH",
                id,
                before,
                after
        );

        return after;
    }

    /**
     * Convert Branch entity to API response.
     *
     * Company is guaranteed to be loaded because all BranchRepository
     * read methods used by this controller fetch company using @EntityGraph.
     */
    private Map<String, Object> view(Branch branch) {

        Company company = branch.getCompany();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", branch.getId());
        response.put("companyId", company.getId());
        response.put("companyName", company.getName());
        response.put("name", branch.getName());
        response.put("code", branch.getCode());
        response.put("phone", Objects.toString(branch.getPhone(), ""));
        response.put("email", Objects.toString(branch.getEmail(), ""));
        response.put("gstin", Objects.toString(branch.getGstin(), ""));
        response.put("website", Objects.toString(branch.getWebsite(), ""));
        response.put("invoiceTitle", Objects.toString(branch.getInvoiceTitle(), ""));
        response.put("invoiceSubtitle", Objects.toString(branch.getInvoiceSubtitle(), ""));
        response.put("invoiceTerms", Objects.toString(branch.getInvoiceTerms(), ""));
        response.put("invoiceFooter", Objects.toString(branch.getInvoiceFooter(), ""));
        response.put("address", Objects.toString(branch.getAddress(), ""));
        response.put("active", branch.isActive());
        return response;
    }

    public record Request(
            Long companyId,
            String name,
            String code,
            String phone,
            String email,
            String gstin,
            String website,
            String invoiceTitle,
            String invoiceSubtitle,
            String invoiceTerms,
            String invoiceFooter,
            String address
    ) {
    }
}