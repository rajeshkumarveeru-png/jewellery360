package com.jewellery360.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.BusinessRecord;
import com.jewellery360.repository.BusinessRecordRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController @RequestMapping("/api/invoices") @RequiredArgsConstructor
public class PdfInvoiceController {
    private final BusinessRecordRepository records; private final ObjectMapper mapper; private final PermissionService permissions;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id){
        BusinessRecord r=records.findById(id).orElseThrow();
        if(!"APP_ADMIN".equals(me.getRole())&&!Objects.equals(r.getCompany().getId(),me.getCompanyId())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Invoice access denied");
        permissions.requireModule(me,"BILLING");
        Map<String,Object> p;
        try{p=mapper.readValue(r.getPayload(),Map.class);}catch(Exception e){p=Map.of();}
        String[] lines={"JEWELLERY360","Invoice: "+r.getTitle(),"Date: "+r.getRecordDate(),"Customer: "+p.getOrDefault("customer",""),"Tag: "+p.getOrDefault("tag",""),
            "Design: "+p.getOrDefault("design",""),"Purity: "+p.getOrDefault("purity",""),"Gross Weight: "+p.getOrDefault("grossWeight",""),
            "Stone Weight: "+p.getOrDefault("stoneWeight",""),"Net Weight: "+p.getOrDefault("netWeight",""),"Gold Rate: "+p.getOrDefault("goldRate",""),
            "Gold Value: "+p.getOrDefault("goldValue",""),"Wastage: "+p.getOrDefault("wastageValue",""),"Making: "+p.getOrDefault("makingCharge",""),
            "Stone: "+p.getOrDefault("stoneCharge",""),"GST: "+p.getOrDefault("gst",""),"TOTAL: "+p.getOrDefault("total",r.getAmount())};
        byte[] body=SimplePdf.text(lines);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename="+r.getTitle()+".pdf").body(body);
    }

    static class SimplePdf {
        static byte[] text(String[] lines){
            StringBuilder content=new StringBuilder("BT /F1 11 Tf 50 780 Td\n");
            for(String line:lines){content.append("(").append(esc(line)).append(") Tj 0 -18 Td\n");}
            content.append("ET");
            String[] objs={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length "+content.length()+" >>\nstream\n"+content+"\nendstream","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"};
            StringBuilder pdf=new StringBuilder("%PDF-1.4\n");List<Integer> offsets=new ArrayList<>();
            for(int i=0;i<objs.length;i++){offsets.add(pdf.length());pdf.append(i+1).append(" 0 obj\n").append(objs[i]).append("\nendobj\n");}
            int xref=pdf.length();pdf.append("xref\n0 ").append(objs.length+1).append("\n0000000000 65535 f \n");
            for(int off:offsets)pdf.append(String.format("%010d 00000 n \n",off));
            pdf.append("trailer\n<< /Size ").append(objs.length+1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
            return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        }
        static String esc(String s){return s.replace("\\","\\\\").replace("(","\\(").replace(")","\\)").replace("\r"," ").replace("\n"," ");}
    }
}
