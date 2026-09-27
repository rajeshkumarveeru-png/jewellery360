package com.jewellery360.controller;

import com.jewellery360.domain.Company;
import com.jewellery360.domain.Branch;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.AuditService;
import com.jewellery360.service.PermissionService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/companies") @RequiredArgsConstructor
public class CompanyController {
    private final CompanyRepository companies; private final BranchRepository branches; private final PermissionService permissions; private final AuditService audit;
    @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal AuthenticatedUser me){
        if(!"APP_ADMIN".equals(me.getRole())) return companies.findById(me.getCompanyId()).map(c->List.of(view(c))).orElse(List.of());
        return companies.findAll().stream().map(this::view).toList();
    }
    @PostMapping public Map<String,Object> create(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody Request r){
        permissions.requireAppAdmin(me);
        Company c=new Company();c.setName(r.name().trim());c.setCode(r.code()==null||r.code().isBlank()?"J360-"+UUID.randomUUID().toString().substring(0,8).toUpperCase():r.code().trim());
        c.setGstin(r.gstin());c.setPhone(r.phone());c.setEmail(r.email());c.setAddress(r.address());c.setActive(true);companies.save(c);
        audit.log(me,"CREATE","COMPANY",c.getId(),null,view(c));return view(c);
    }
    @PutMapping("/{id}") public Map<String,Object> update(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,@RequestBody Request r){
        permissions.requireAppAdmin(me); Company c=companies.findById(id).orElseThrow();Map<String,Object> before=view(c);
        c.setName(r.name().trim());c.setGstin(r.gstin());c.setPhone(r.phone());c.setEmail(r.email());c.setAddress(r.address());companies.save(c);
        audit.log(me,"UPDATE","COMPANY",id,before,view(c));return view(c);
    }
    @PostMapping("/{id}/activate") public Map<String,Object> activate(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id){permissions.requireAppAdmin(me);Company c=companies.findById(id).orElseThrow();c.setActive(true);companies.save(c);audit.log(me,"ACTIVATE","COMPANY",id,null,view(c));return view(c);}
    private Map<String,Object> view(Company c){return Map.of("id",c.getId(),"name",c.getName(),"code",c.getCode(),"gstin",Objects.toString(c.getGstin(),""),"phone",Objects.toString(c.getPhone(),""),"email",Objects.toString(c.getEmail(),""),"address",Objects.toString(c.getAddress(),""),"active",c.isActive());}
    public record Request(@NotBlank String name,String code,String gstin,String phone,String email,String address){}
}
