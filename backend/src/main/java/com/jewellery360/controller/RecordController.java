package com.jewellery360.controller;

import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/records") @RequiredArgsConstructor
public class RecordController {
    private final RecordService records;
    @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal AuthenticatedUser me,@RequestParam String module,
        @RequestHeader(value="X-Company-Id",required=false) Long companyId,@RequestHeader(value="X-Branch-Id",required=false) Long branchId){return records.list(me,module,companyId,branchId);}
    @PostMapping public Map<String,Object> create(@AuthenticationPrincipal AuthenticatedUser me,@RequestBody RecordService.Request r,
        @RequestHeader(value="X-Company-Id",required=false) Long companyId,@RequestHeader(value="X-Branch-Id",required=false) Long branchId){return records.create(me,r,companyId,branchId);}
    @PutMapping("/{id}") public Map<String,Object> update(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,@RequestBody RecordService.Request r,
        @RequestHeader(value="X-Company-Id",required=false) Long companyId,@RequestHeader(value="X-Branch-Id",required=false) Long branchId){return records.update(me,id,r,companyId,branchId);}
    @DeleteMapping("/{id}") public Map<String,Object> delete(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,
        @RequestHeader(value="X-Company-Id",required=false) Long companyId,@RequestHeader(value="X-Branch-Id",required=false) Long branchId){records.delete(me,id,companyId,branchId);return Map.of("message","Record cancelled/deleted.");}
}
