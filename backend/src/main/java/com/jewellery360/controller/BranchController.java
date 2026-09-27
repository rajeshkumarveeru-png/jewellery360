package com.jewellery360.controller;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/branches") @RequiredArgsConstructor
public class BranchController {
    private final BranchRepository branches; private final CompanyRepository companies; private final PermissionService permissions; private final AuditService audit;
    @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal AuthenticatedUser me){
        if("APP_ADMIN".equals(me.getRole())) return branches.findAll().stream().map(this::view).toList();
        return branches.findByCompanyId(me.getCompanyId()).stream().map(this::view).toList();
    }
    @PostMapping public Map<String,Object> create(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody Request r){
        Long cid="APP_ADMIN".equals(me.getRole())?r.companyId():me.getCompanyId();
        if("APP_ADMIN".equals(me.getRole())) permissions.requireAppAdmin(me); else if(!"COMPANY_ADMIN".equals(me.getRole())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Admin only");
        Company c=companies.findById(cid).orElseThrow();Branch b=new Branch();b.setCompany(c);b.setName(r.name());b.setCode(r.code());b.setPhone(r.phone());b.setAddress(r.address());b.setActive(true);branches.save(b);
        audit.log(me,"CREATE","BRANCH",b.getId(),null,view(b));return view(b);
    }
    @PutMapping("/{id}") public Map<String,Object> update(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,@RequestBody Request r){
        Branch b=branches.findById(id).orElseThrow(); if(!"APP_ADMIN".equals(me.getRole())&&!Objects.equals(me.getCompanyId(),b.getCompany().getId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Branch access denied");
        if(!"APP_ADMIN".equals(me.getRole())&&! "COMPANY_ADMIN".equals(me.getRole())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Admin only");
        Map<String,Object> before=view(b);b.setName(r.name());b.setCode(r.code());b.setPhone(r.phone());b.setAddress(r.address());branches.save(b);audit.log(me,"UPDATE","BRANCH",id,before,view(b));return view(b);
    }
    private Map<String,Object> view(Branch b){return Map.of("id",b.getId(),"companyId",b.getCompany().getId(),"companyName",b.getCompany().getName(),"name",b.getName(),"code",b.getCode(),"phone",Objects.toString(b.getPhone(),""),"address",Objects.toString(b.getAddress(),""),"active",b.isActive());}
    public record Request(Long companyId,String name,String code,String phone,String address){}
}
