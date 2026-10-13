package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.BulkImportService;
import com.jewellery360.service.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stock/import")
@RequiredArgsConstructor
public class BulkJewelleryImportController {
    private final BulkImportService imports;
    private final TenantContext tenant;

    @GetMapping("/template")
    public ResponseEntity<String> template() {
        String csv = """
                Product Name,SKU,Category,Design,Purity,Tag No,Barcode,Gross Weight,Stone Weight,Net Weight,HUID,Wastage %,Making Type,Making Value,Stone Value,Cost Price,Location
                Gold Ring,,Rings,,22K,,,4.250,0.000,4.250,,8,FIXED,850,0,,Tray 1
                Gold Chain,,Chains,,22K,,,18.500,0.000,18.500,,10,PER_GRAM,350,0,,Tray 2
                """;
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"jewellery-stock-import-template.csv\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csv);
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) {
        return imports.preview(tenant.withBranch(me), body);
    }

    @PostMapping("/commit")
    public Map<String, Object> commit(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) {
        return imports.commit(tenant.withBranch(me), body);
    }
}
