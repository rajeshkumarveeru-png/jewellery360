package com.jewellery360.controller;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "jewell360", "version", "1.0.0");
    }
}
