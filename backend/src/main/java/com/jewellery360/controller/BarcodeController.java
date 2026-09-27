package com.jewellery360.controller;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/barcode") @RequiredArgsConstructor
public class BarcodeController {
 private final JewelleryTagRepository tags; private final JewelleryItemRepository items; private final PermissionService permissions;
 @GetMapping("/scan/{value}") public Map<String,Object> scan(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable String value){permissions.requireModule(me,"JEWELLERY");Long cid=me.getCompanyId();if("APP_ADMIN".equals(me.getRole()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"APP_ADMIN requires company/branch context header for barcode scan");if(cid==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Company scope required");JewelleryTag t=tags.findByCompanyIdAndBarcode(cid,value).orElseGet(()->tags.findByCompanyIdAndTagNo(cid,value).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Barcode/tag not found")));JewelleryItem i=items.findByCompanyId(cid).stream().filter(x->x.getTag().getId().equals(t.getId())).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tagged jewellery item not found"));if(me.getBranchId()!=null&&!Objects.equals(i.getBranch().getId(),me.getBranchId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch access denied");return Map.of("tagId",t.getId(),"tagNo",t.getTagNo(),"barcode",t.getBarcode(),"itemId",i.getId(),"status",i.getStatus(),"grossWeight",i.getGrossWeight(),"netWeight",i.getNetWeight(),"huid",Objects.toString(i.getHuid(),""),"purity",i.getPurity().getKarat()!=null?i.getPurity().getKarat():i.getPurity().getName());}
}
