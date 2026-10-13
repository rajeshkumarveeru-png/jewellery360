package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Orders, repairs and karigar job work (the Services page). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ServicesController {
    private final OrdersService orders;
    private final KarigarService karigars;
    private final TenantContext tenant;

    @GetMapping("/orders") public List<Map<String, Object>> orders(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String status) { return orders.orders(tenant.of(me), status); }
    @GetMapping("/orders/{id}") public Map<String, Object> order(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return orders.order(tenant.of(me), id); }
    @PostMapping("/orders") public Map<String, Object> createOrder(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return orders.createOrder(tenant.withBranch(me), b); }
    @PutMapping("/orders/{id}") public Map<String, Object> updateOrder(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return orders.updateOrder(tenant.of(me), id, b); }
    @PostMapping("/orders/{id}/advances") public Map<String, Object> orderAdvance(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return orders.orderAdvance(tenant.withBranch(me), id, b); }

    @GetMapping("/repairs") public List<Map<String, Object>> repairs(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String status) { return orders.repairs(tenant.of(me), status); }
    @GetMapping("/repairs/{id}") public Map<String, Object> repair(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return orders.repair(tenant.of(me), id); }
    @PostMapping("/repairs") public Map<String, Object> createRepair(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return orders.createRepair(tenant.withBranch(me), b); }
    @PutMapping("/repairs/{id}") public Map<String, Object> updateRepair(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return orders.updateRepair(tenant.withBranch(me), id, b); }

    @GetMapping("/karigars/summary") public List<Map<String, Object>> karigars(@AuthenticationPrincipal AuthenticatedUser me) { return karigars.karigars(tenant.of(me)); }
    @GetMapping("/karigars/{id}") public Map<String, Object> karigar(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return karigars.karigar(tenant.of(me), id); }
    @PostMapping("/karigars/{id}/payments") public Map<String, Object> payKarigar(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return karigars.pay(tenant.withBranch(me), id, b); }
    @GetMapping("/jobs") public List<Map<String, Object>> jobs(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) Long karigarId, @RequestParam(required = false) String status) { return karigars.jobs(tenant.of(me), karigarId, status); }
    @GetMapping("/jobs/{id}") public Map<String, Object> job(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) { return karigars.job(tenant.of(me), id); }
    @PostMapping("/jobs") public Map<String, Object> createJob(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode b) { return karigars.createJob(tenant.withBranch(me), b); }
    @PostMapping("/jobs/{id}/metal") public Map<String, Object> metal(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return karigars.addMovement(tenant.of(me), id, b); }
    @PostMapping("/jobs/{id}/close") public Map<String, Object> close(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode b) { return karigars.close(tenant.of(me), id, b); }
}
