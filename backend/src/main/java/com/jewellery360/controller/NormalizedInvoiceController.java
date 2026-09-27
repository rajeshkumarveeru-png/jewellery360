package com.jewellery360.controller;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController @RequestMapping("/api/invoices") @RequiredArgsConstructor
public class NormalizedInvoiceController {
    private final SaleRepository sales; private final SaleReturnRepository returns; private final SaleItemRepository items; private final PermissionService permissions;
    @GetMapping("/sales/{id}/pdf")
    public ResponseEntity<byte[]> salePdf(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id){
        permissions.requireModule(me,"BILLING"); Sale s=sales.findById(id).orElseThrow(()->notFound("Sale not found")); check(me,s.getCompany().getId(),s.getBranch().getId());
        List<String> lines=new ArrayList<>(); lines.add("JEWELLERY360"); lines.add("Invoice: "+s.getInvoiceNo()); lines.add("Date: "+s.getSaleDate()); lines.add("Customer: "+s.getCustomer().getName()); lines.add("Phone: "+Objects.toString(s.getCustomer().getPhone(),"")); lines.add("----------------------------------------");
        for(SaleItem i:items.findBySaleId(s.getId())) lines.add("Tag "+i.getTagNo()+" | Net "+i.getNetWeight()+" | Rate "+i.getGoldRate()+" | Total "+i.getTotal());
        lines.add("----------------------------------------"); lines.add("Subtotal: "+s.getSubtotal()); lines.add("Discount: "+s.getDiscount()); lines.add("GST: "+s.getGst()); lines.add("TOTAL: "+s.getTotal()); lines.add("Payment: "+s.getPaymentStatus());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename="+s.getInvoiceNo()+".pdf").body(SimplePdf.text(lines));
    }
    @GetMapping("/returns/{id}/pdf")
    public ResponseEntity<byte[]> returnPdf(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id){
        permissions.requireModule(me,"BILLING"); SaleReturn r=returns.findById(id).orElseThrow(()->notFound("Return not found")); check(me,r.getCompany().getId(),r.getBranch().getId());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename="+r.getReturnNo()+".pdf").body(SimplePdf.text(List.of("JEWELLERY360","Sales Return: "+r.getReturnNo(),"Date: "+r.getReturnDate(),"Original Invoice: "+r.getSale().getInvoiceNo(),"Customer: "+r.getSale().getCustomer().getName(),"Amount: "+r.getAmount(),"Reason: "+Objects.toString(r.getReason(),""))));
    }
    private void check(AuthenticatedUser me,Long companyId,Long branchId){ if(!"APP_ADMIN".equals(me.getRole()) && !Objects.equals(companyId,me.getCompanyId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invoice access denied"); if(!"APP_ADMIN".equals(me.getRole())&&me.getBranchId()!=null&&!Objects.equals(branchId,me.getBranchId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch access denied"); }
    private ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s);}
    static class SimplePdf {
      static byte[] text(List<String> lines){StringBuilder c=new StringBuilder("BT /F1 10 Tf 45 790 Td\n");for(String l:lines)c.append("(").append(esc(l)).append(") Tj 0 -16 Td\n");c.append("ET");String[] o={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>","<< /Length "+c.length()+" >>\nstream\n"+c+"\nendstream","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"};StringBuilder p=new StringBuilder("%PDF-1.4\n");List<Integer> off=new ArrayList<>();for(int i=0;i<o.length;i++){off.add(p.length());p.append(i+1).append(" 0 obj\n").append(o[i]).append("\nendobj\n");}int x=p.length();p.append("xref\n0 ").append(o.length+1).append("\n0000000000 65535 f \n");for(int n:off)p.append(String.format("%010d 00000 n \n",n));p.append("trailer\n<< /Size ").append(o.length+1).append(" /Root 1 0 R >>\nstartxref\n").append(x).append("\n%%EOF");return p.toString().getBytes(StandardCharsets.ISO_8859_1);}
      static String esc(String s){return s.replace("\\","\\\\").replace("(","\\(").replace(")","\\)").replace("\r"," ").replace("\n"," ");}
    }
}
