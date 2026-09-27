package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class RecordService {
    private final BusinessRecordRepository records; private final CompanyRepository companies; private final BranchRepository branches; private final AppUserRepository users;
    private final PermissionService permissions; private final AuditService audit; private final ObjectMapper mapper;

    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(AuthenticatedUser me,String module,Long contextCompany,Long contextBranch){
        permissions.requireModule(me,module);
        Scope s=scope(me,contextCompany,contextBranch);
        List<BusinessRecord> list;
        if(s.companyId==null) list=records.findAll();
        else if(s.branchId==null) list=records.findByCompanyIdAndModuleOrderByRecordDateDescIdDesc(s.companyId,module);
        else list=records.findByCompanyIdAndBranchIdAndModuleOrderByRecordDateDescIdDesc(s.companyId,s.branchId,module);
        return list.stream().map(this::view).toList();
    }
    @Transactional
    public Map<String,Object> create(AuthenticatedUser me,Request r,Long contextCompany,Long contextBranch){
        permissions.requireWrite(me,r.module()); Scope s=scope(me,contextCompany,contextBranch);
        if(s.companyId==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Company context is required");
        Company c=companies.findById(s.companyId).orElseThrow(); Branch b=branches.findById(s.branchId).orElseThrow();
        if(!Objects.equals(b.getCompany().getId(),c.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid branch context");
        BusinessRecord x=new BusinessRecord();x.setModule(r.module().toUpperCase());x.setSubtype(r.subtype());x.setTitle(r.title());x.setStatus(r.status()==null?"ACTIVE":r.status());
        x.setAmount(n(r.amount()));x.setQuantity(n(r.quantity(),BigDecimal.ONE));x.setRecordDate(r.date()==null?LocalDate.now():r.date());x.setOwner(me.getUsername());x.setNotes(r.notes());
        x.setCompany(c);x.setBranch(b);x.setCreatedBy(users.findById(me.getUserId()).orElse(null));x.setPayload(r.payload()==null?"{}":r.payload());records.save(x);
        audit.log(me,"CREATE","BUSINESS_RECORD",x.getId(),null,view(x));return view(x);
    }
    @Transactional
    public Map<String,Object> update(AuthenticatedUser me,Long id,Request r,Long contextCompany,Long contextBranch){
        BusinessRecord x=records.findById(id).orElseThrow(); Scope s=scope(me,contextCompany,contextBranch);checkScope(me,x,s);permissions.requireWrite(me,x.getModule());
        Map<String,Object> before=view(x);x.setTitle(r.title());x.setStatus(r.status());x.setAmount(n(r.amount()));x.setQuantity(n(r.quantity(),BigDecimal.ONE));x.setRecordDate(r.date()==null?x.getRecordDate():r.date());x.setNotes(r.notes());x.setPayload(r.payload()==null?x.getPayload():r.payload());records.save(x);
        audit.log(me,"UPDATE","BUSINESS_RECORD",id,before,view(x));return view(x);
    }
    @Transactional
    public void delete(AuthenticatedUser me,Long id,Long contextCompany,Long contextBranch){
        BusinessRecord x=records.findById(id).orElseThrow();Scope s=scope(me,contextCompany,contextBranch);checkScope(me,x,s);permissions.requireWrite(me,x.getModule());
        Map<String,Object> before=view(x);x.setStatus("DELETED");records.save(x);audit.log(me,"DELETE","BUSINESS_RECORD",id,before,view(x));
    }
    private void checkScope(AuthenticatedUser me,BusinessRecord x,Scope s){if(!"APP_ADMIN".equals(me.getRole()) && !Objects.equals(x.getCompany().getId(),s.companyId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Company access denied"); if(s.branchId!=null&&!Objects.equals(x.getBranch().getId(),s.branchId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch access denied");}
    private Scope scope(AuthenticatedUser me,Long ctxC,Long ctxB){if("APP_ADMIN".equals(me.getRole())){if(ctxC==null||ctxB==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Company and branch context are required");return new Scope(ctxC,ctxB);}return new Scope(me.getCompanyId(),me.getBranchId());}
    private BigDecimal n(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
    private BigDecimal n(BigDecimal x,BigDecimal d){return x==null?d:x;}
    private Map<String,Object> view(BusinessRecord x){Map<String,Object> m=new LinkedHashMap<>();m.put("id",x.getId());m.put("module",x.getModule());m.put("subtype",x.getSubtype());m.put("title",x.getTitle());m.put("status",x.getStatus());m.put("amount",x.getAmount());m.put("quantity",x.getQuantity());m.put("date",x.getRecordDate());m.put("owner",x.getOwner());m.put("notes",x.getNotes());m.put("companyId",x.getCompany()==null?null:x.getCompany().getId());m.put("companyName",x.getCompany()==null?null:x.getCompany().getName());m.put("branchId",x.getBranch()==null?null:x.getBranch().getId());m.put("branchName",x.getBranch()==null?null:x.getBranch().getName());try{m.put("payload",mapper.readTree(x.getPayload()==null?"{}":x.getPayload()));}catch(Exception e){m.put("payload",Map.of());}return m;}
    private record Scope(Long companyId,Long branchId){}
    public record Request(String module,String subtype,String title,String status,BigDecimal amount,BigDecimal quantity,LocalDate date,String notes,String payload){}
}
