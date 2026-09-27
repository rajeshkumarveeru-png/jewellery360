package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.BusinessRecord;
import com.jewellery360.repository.BusinessRecordRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController @RequestMapping("/api/dashboard") @RequiredArgsConstructor
public class DashboardController {
    private final BusinessRecordRepository records; private final PermissionService permissions; private final ObjectMapper mapper;
    @GetMapping public Map<String,Object> dashboard(@AuthenticationPrincipal AuthenticatedUser me,@RequestHeader(value="X-Company-Id",required=false) Long companyId,@RequestHeader(value="X-Branch-Id",required=false) Long branchId){
        List<BusinessRecord> all;
        if("APP_ADMIN".equals(me.getRole())){if(companyId==null||branchId==null) return Map.of("platform",true,"message","Select company and branch context."); all=records.findByCompanyIdAndBranchIdOrderByRecordDateDescIdDesc(companyId,branchId);}
        else if(branchId!=null) all=records.findByCompanyIdAndBranchIdOrderByRecordDateDescIdDesc(me.getCompanyId(),me.getBranchId());
        else all=records.findByCompanyIdOrderByRecordDateDescIdDesc(me.getCompanyId());
        BigDecimal sales=all.stream().filter(x->"BILLING".equals(x.getModule())&&!"DELETED".equals(x.getStatus())).map(BusinessRecord::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal oldGold=all.stream().filter(x->"OLD GOLD".equals(x.getModule())).map(BusinessRecord::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        long customers=all.stream().filter(x->"CUSTOMERS".equals(x.getModule())&&! "DELETED".equals(x.getStatus())).count();
        BigDecimal inventory=all.stream().filter(x->"INVENTORY".equals(x.getModule())).map(BusinessRecord::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal outstanding=all.stream().filter(x->"PAYMENTS".equals(x.getModule())&&"DUE".equalsIgnoreCase(x.getStatus())).map(BusinessRecord::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal goldSold=BigDecimal.ZERO,silverSold=BigDecimal.ZERO;
        for(BusinessRecord x:all) if("BILLING".equals(x.getModule())) try{JsonNode p=mapper.readTree(x.getPayload());goldSold=goldSold.add(p.path("goldWeight").decimalValue());silverSold=silverSold.add(p.path("silverWeight").decimalValue());}catch(Exception ignored){}
        return Map.of("platform",false,"todaySales",sales,"goldSold",goldSold,"silverSold",silverSold,"itemsSold",all.stream().filter(x->"BILLING".equals(x.getModule())).map(BusinessRecord::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add),"newCustomers",customers,"outstanding",outstanding,"oldGoldPurchased",oldGold,"inventoryUnits",inventory,"recent",all.stream().limit(10).map(x->Map.of("id",x.getId(),"module",x.getModule(),"title",x.getTitle(),"amount",x.getAmount(),"date",x.getRecordDate())).toList());
    }
}
