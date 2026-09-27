package com.jewellery360.controller;
import com.jewellery360.domain.AuditHistory;
import com.jewellery360.repository.AuditHistoryRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/audit") @RequiredArgsConstructor
public class AuditController{
 private final AuditHistoryRepository audits; private final PermissionService permissions;
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal AuthenticatedUser me,@RequestHeader(value="X-Company-Id",required=false) Long companyId){
   List<AuditHistory> a;
   if("APP_ADMIN".equals(me.getRole())&&companyId==null)a=audits.findTop200ByOrderByCreatedAtDesc();
   else {Long cid="APP_ADMIN".equals(me.getRole())?companyId:me.getCompanyId();a=audits.findTop200ByCompanyIdOrderByCreatedAtDesc(cid);}
   return a.stream().map(x->{
     Map<String,Object> m=new LinkedHashMap<>();
     m.put("id",x.getId());
     m.put("createdAt",x.getCreatedAt());
     m.put("action",x.getAction());
     m.put("entityType",x.getEntityType());
     m.put("entityId",x.getEntityId()==null?"":x.getEntityId());
     m.put("user",x.getUser()==null?"":x.getUser().getUsername());
     m.put("company",x.getCompany()==null?"":x.getCompany().getName());
     m.put("branch",x.getBranch()==null?"":x.getBranch().getName());
     return m;
   }).toList();
 }
}
