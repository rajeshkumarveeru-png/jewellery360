package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.Phase3WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/phase3")
@RequiredArgsConstructor
public class Phase3WorkflowController {
    private final Phase3WorkflowService service;

    @PostMapping("/sales") public Map<String,Object> sale(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody JsonNode n){return service.completeSale(me,n);}
    @PostMapping("/sales/{id}/returns") public Map<String,Object> saleReturn(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,@RequestBody JsonNode n){return service.returnSale(me,id,n);}
    @PostMapping("/transfers") public Map<String,Object> transfer(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody JsonNode n){return service.createTransfer(me,n);}
    @PostMapping("/transfers/{id}/complete") public Map<String,Object> transferComplete(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id){return service.completeTransfer(me,id);}
    @PostMapping("/purchases") public Map<String,Object> purchase(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody JsonNode n){return service.createPurchase(me,n);}
    @PostMapping("/purchases/{id}/returns") public Map<String,Object> purchaseReturn(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,@RequestBody JsonNode n){return service.returnPurchase(me,id,n);}
    @PostMapping("/old-gold") public Map<String,Object> oldGold(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody JsonNode n){return service.createOldGold(me,n);}
    @PatchMapping("/repairs/{id}") public Map<String,Object> repair(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,@RequestBody JsonNode n){return service.updateRepair(me,id,n);}
    @PatchMapping("/custom-orders/{id}") public Map<String,Object> customOrder(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,@RequestBody JsonNode n){return service.updateCustomOrder(me,id,n);}
    @PostMapping("/advances") public Map<String,Object> advance(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody JsonNode n){return service.createAdvance(me,n);}
    @GetMapping("/customers/{id}/outstanding") public Map<String,Object> outstanding(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id){return service.customerOutstanding(me,id);}
    @GetMapping("/reports/{type}") public Map<String,Object> report(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable String type){return service.normalizedReport(me,type);}
}
