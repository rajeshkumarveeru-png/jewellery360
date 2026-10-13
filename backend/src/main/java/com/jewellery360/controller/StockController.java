package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.MastersService;
import com.jewellery360.service.SkuService;
import com.jewellery360.service.StockService;
import com.jewellery360.service.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Stock (tagged pieces) and the master lists behind it. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StockController {
    private final StockService stock;
    private final MastersService masters;
    private final SkuService sku;
    private final TenantContext tenant;

    @GetMapping("/stock/items")
    public List<Map<String, Object>> items(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean allBranches,
                                           @RequestParam(defaultValue = "500") int limit) {
        return stock.list(tenant.of(me), status, q, allBranches, limit);
    }

    @GetMapping("/stock/items/{id}") public Map<String, Object> item(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return stock.get(tenant.of(me), id); }
    @GetMapping("/stock/items/{id}/timeline") public Map<String, Object> timeline(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return stock.timeline(tenant.of(me), id); }
    @GetMapping("/stock/scan") public Map<String, Object> scan(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam String code) { return stock.scan(tenant.of(me), code); }
    @PostMapping("/stock/items") public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return stock.create(tenant.withBranch(me), body); }
    @PutMapping("/stock/items/{id}") public Map<String, Object> update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) { return stock.update(tenant.of(me), id, body); }
    @PostMapping("/stock/items/{id}/remove") public Map<String, Object> remove(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) { return stock.remove(tenant.of(me), id, body); }
    @PostMapping("/stock/items/{id}/restore") public Map<String, Object> restore(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return stock.restore(tenant.of(me), id); }
    @GetMapping("/stock/transfers") public List<Map<String, Object>> transfers(@AuthenticationPrincipal AuthenticatedUser me) { return stock.transfers(tenant.of(me)); }
    @PostMapping("/stock/transfers") public Map<String, Object> transfer(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return stock.transfer(tenant.withBranch(me), body); }
    @GetMapping("/stock/next-tag") public Map<String, String> nextTag(@AuthenticationPrincipal AuthenticatedUser me) { return Map.of("tagNo", sku.nextTagNo(tenant.of(me).company())); }

    @GetMapping("/masters") public Map<String, Object> masters(@AuthenticationPrincipal AuthenticatedUser me) { return masters.all(tenant.of(me)); }
    @PostMapping("/masters/categories") public Map<String, Object> cat(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return masters.saveCategory(tenant.of(me), null, b); }
    @PutMapping("/masters/categories/{id}") public Map<String, Object> cat(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return masters.saveCategory(tenant.of(me), id, b); }
    @PostMapping("/masters/designs") public Map<String, Object> design(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return masters.saveDesign(tenant.of(me), null, b); }
    @PutMapping("/masters/designs/{id}") public Map<String, Object> design(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return masters.saveDesign(tenant.of(me), id, b); }
    @PostMapping("/masters/products") public Map<String, Object> product(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return masters.saveProduct(tenant.of(me), null, b); }
    @PutMapping("/masters/products/{id}") public Map<String, Object> product(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return masters.saveProduct(tenant.of(me), id, b); }
    @PostMapping("/masters/purities") public Map<String, Object> purity(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return masters.savePurity(tenant.of(me), null, b); }
    @PutMapping("/masters/purities/{id}") public Map<String, Object> purity(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return masters.savePurity(tenant.of(me), id, b); }
    @PostMapping("/masters/suppliers") public Map<String, Object> supplier(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return masters.saveSupplier(tenant.of(me), null, b); }
    @PutMapping("/masters/suppliers/{id}") public Map<String, Object> supplier(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return masters.saveSupplier(tenant.of(me), id, b); }
    @PostMapping("/masters/karigars") public Map<String, Object> karigar(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return masters.saveKarigar(tenant.of(me), null, b); }
    @PutMapping("/masters/karigars/{id}") public Map<String, Object> karigar(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return masters.saveKarigar(tenant.of(me), id, b); }
}
