package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

/** Database backups - platform administrator only. */
@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {
    private final BackupService backups;

    @GetMapping public Map<String, Object> status(@AuthenticationPrincipal AuthenticatedUser me) { return backups.status(me); }
    @PostMapping public Map<String, Object> backup(@AuthenticationPrincipal AuthenticatedUser me) { return backups.backupNow(me); }
    @GetMapping("/folders") public Map<String, Object> folders(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String path) { return backups.folders(me, path); }
    @PutMapping("/settings") public Map<String, Object> settings(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode body) { return backups.updateSettings(me, body); }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam("file") MultipartFile file) { return backups.upload(me, file); }
    @PostMapping("/{id}/restore") public Map<String, Object> restore(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode body) {
        return backups.restore(me, id, body.path("confirm").asText(null));
    }
    @GetMapping("/{id}/download") public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        Path p = backups.file(me, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + p.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(p));
    }
}
