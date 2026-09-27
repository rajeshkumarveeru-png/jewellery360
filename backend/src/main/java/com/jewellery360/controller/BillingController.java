package com.jewellery360.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.RecordService;
import com.jewellery360.service.JewelleryBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.*;
import java.util.*;

@RestController @RequestMapping("/api/billing") @RequiredArgsConstructor
public class BillingController {
    private final RecordService records; private final ObjectMapper mapper; private final JewelleryBillingService jewelleryBilling;

    @PostMapping("/calculate")
    public Map<String,Object> calculate(@RequestBody BillingRequest r){ return calculation(r); }

    @PostMapping("/domain")
    public Map<String,Object> createDomain(@AuthenticationPrincipal AuthenticatedUser me,
        @RequestBody JewelleryBillingService.Request r,
        @RequestHeader(value="X-Company-Id",required=false) Long companyId,
        @RequestHeader(value="X-Branch-Id",required=false) Long branchId) {
        return jewelleryBilling.create(me,r,companyId,branchId);
    }

    @PostMapping("/domain/{id}/cancel")
    public Map<String,Object> cancelDomain(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,
        @RequestHeader(value="X-Company-Id",required=false) Long companyId,
        @RequestHeader(value="X-Branch-Id",required=false) Long branchId) {
        return jewelleryBilling.cancel(me,id,companyId,branchId);
    }

    @PostMapping
    public Map<String,Object> create(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody BillingRequest r,
        @RequestHeader(value="X-Company-Id",required=false) Long companyId,@RequestHeader(value="X-Branch-Id",required=false) Long branchId) throws Exception {
        Map<String,Object> c=calculation(r);
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("invoiceNo",r.invoiceNo());payload.put("customer",r.customer());payload.put("tag",r.tag());payload.put("design",r.design());
        payload.put("purity",r.purity());payload.put("grossWeight",r.grossWeight());payload.put("stoneWeight",r.stoneWeight());payload.put("netWeight",r.netWeight());
        payload.put("goldRate",r.goldRate());payload.put("wastagePercent",r.wastagePercent());payload.put("makingCharge",r.makingCharge());payload.put("stoneCharge",r.stoneCharge());
        payload.put("otherCharges",r.otherCharges());payload.put("gstPercent",r.gstPercent());payload.put("paymentMode",r.paymentMode());
        payload.put("goldValue",c.get("goldValue"));payload.put("wastageValue",c.get("wastageValue"));payload.put("gst",c.get("gst"));payload.put("total",c.get("total"));
        RecordService.Request req=new RecordService.Request("BILLING","SALE",r.invoiceNo(), "COMPLETED",new BigDecimal(c.get("total").toString()),BigDecimal.ONE,r.date(), "Jewellery invoice",mapper.writeValueAsString(payload));
        return records.create(me,req,companyId,branchId);
    }
    private Map<String,Object> calculation(BillingRequest r){
        BigDecimal net=n(r.netWeight()),rate=n(r.goldRate()),gold=net.multiply(rate);
        BigDecimal wastage=gold.multiply(n(r.wastagePercent())).divide(BigDecimal.valueOf(100),3,RoundingMode.HALF_UP);
        BigDecimal subtotal=gold.add(n(r.makingCharge())).add(n(r.stoneCharge())).add(n(r.otherCharges())).add(wastage);
        BigDecimal gst=subtotal.multiply(n(r.gstPercent())).divide(BigDecimal.valueOf(100),3,RoundingMode.HALF_UP);
        BigDecimal total=subtotal.add(gst);
        return Map.of("goldValue",gold,"wastageValue",wastage,"subtotal",subtotal,"gst",gst,"total",total);
    }
    private BigDecimal n(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    public record BillingRequest(String invoiceNo,String customer,String tag,String design,String purity,BigDecimal grossWeight,BigDecimal stoneWeight,BigDecimal netWeight,
        BigDecimal goldRate,BigDecimal wastagePercent,BigDecimal makingCharge,BigDecimal stoneCharge,BigDecimal otherCharges,BigDecimal gstPercent,String paymentMode,java.time.LocalDate date){}
}
