package com.jewellery360.controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.jewellery360.domain.BusinessRecord;
import com.jewellery360.repository.BusinessRecordRepository;
import com.jewellery360.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/reports") @RequiredArgsConstructor
public class ReportsController {
    private final BusinessRecordRepository records;
    @GetMapping
    public Map<String,Object> report(@AuthenticationPrincipal AuthenticatedUser me,@RequestParam String type,
        @RequestHeader(value="X-Company-Id",required=false) Long companyId,@RequestHeader(value="X-Branch-Id",required=false) Long branchId,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){
        Long cid="APP_ADMIN".equals(me.getRole())?companyId:me.getCompanyId();
        if(cid==null) return Map.of("type",type,"rows",List.of());
        List<BusinessRecord> all=branchId!=null?records.findByCompanyIdAndBranchIdOrderByRecordDateDescIdDesc(cid,branchId):records.findByCompanyIdOrderByRecordDateDescIdDesc(cid);
        if(from!=null) all=all.stream().filter(x->!x.getRecordDate().isBefore(from)).toList();
        if(to!=null) all=all.stream().filter(x->!x.getRecordDate().isAfter(to)).toList();
        if("SALES".equalsIgnoreCase(type)) all=all.stream().filter(x->"BILLING".equals(x.getModule())).toList();
        if("STOCK".equalsIgnoreCase(type)) all=all.stream().filter(x->"INVENTORY".equals(x.getModule())).toList();
        if("GOLD".equalsIgnoreCase(type)) all=all.stream().filter(x->Set.of("BILLING","GOLD & RATES","OLD GOLD").contains(x.getModule())).toList();
        if("PROFIT".equalsIgnoreCase(type)) all=all.stream().filter(x->Set.of("BILLING","PAYMENTS","PURCHASES").contains(x.getModule())).toList();
        BigDecimal total=all.stream().map(BusinessRecord::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal qty=all.stream().map(BusinessRecord::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<Map<String,Object>> rows = all.stream()
            .limit(200)
            .map(x -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", x.getId());
                row.put("module", x.getModule());
                row.put("title", x.getTitle());
                row.put("status", x.getStatus());
                row.put("amount", x.getAmount());
                row.put("quantity", x.getQuantity());
                row.put("date", x.getRecordDate());
                row.put("branch", x.getBranch() == null ? "" : x.getBranch().getName());
                return row;
            })
            .toList();
        return Map.of("type",type,"total",total,"quantity",qty,"count",all.size(),"rows",rows);
    }
}
