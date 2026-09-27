package com.jewellery360.controller;

import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.service.PermissionService;
import com.jewellery360.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController @RequestMapping("/api/whatsapp") @RequiredArgsConstructor
public class WhatsAppController {
    private final WhatsAppService whatsapp; private final SaleRepository sales; private final SaleItemRepository saleItems; private final WhatsappLogRepository logs; private final PermissionService permissions;
    @PostMapping("/send")
    public Map<String,Object> send(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody Request r){
        if(me.getCompanyId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Company context required");
        whatsapp.sendText(me.getCompanyId(),r.phone(),r.message());
        return Map.of("message","WhatsApp message submitted.");
    }
    @PostMapping("/sales/{id}")
    public Map<String,Object> sendSale(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id){
        permissions.requireAction(me,"WHATSAPP","SEND"); Sale s=sales.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Sale not found"));
        if(!"APP_ADMIN".equals(me.getRole())&&!java.util.Objects.equals(s.getCompany().getId(),me.getCompanyId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Sale access denied");
        String phone=s.getCustomer().getPhone(); if(phone==null||phone.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Customer phone is missing");
        StringBuilder msg=new StringBuilder("Jewellery360 invoice ").append(s.getInvoiceNo()).append("\nCustomer: ").append(s.getCustomer().getName()).append("\nTotal: ").append(s.getTotal()).append("\nPayment: ").append(s.getPaymentStatus());
        try { whatsapp.sendText(s.getCompany().getId(),phone,msg.toString()); log(s,"SENT",null); return Map.of("status","SENT","invoiceNo",s.getInvoiceNo()); } catch(Exception e){ log(s,"FAILED",e.getMessage()); throw e; }
    }
    private void log(Sale s,String status,String error){WhatsappLog l=new WhatsappLog();l.setCompany(s.getCompany());l.setBranch(s.getBranch());l.setCustomer(s.getCustomer());l.setPhone(s.getCustomer().getPhone());l.setMessageType("INVOICE");l.setReferenceType("SALE");l.setReferenceId(s.getId());l.setStatus(status);l.setErrorMessage(error);logs.save(l);}
    public record Request(String phone,String message){}
}
