package com.bracit.fisprocess.controller;
import com.bracit.fisprocess.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/budgets") @RequiredArgsConstructor
public class BudgetController {
    private final BudgetService svc;
    @PostMapping public ResponseEntity<?> create(@RequestHeader("X-Tenant-Id") UUID t, @RequestHeader(value="X-Actor-Id",defaultValue="system") String p, @RequestBody com.bracit.fisprocess.dto.request.CreateBudgetRequestDto req) { return ResponseEntity.status(201).body(svc.create(t, req, p)); }
    @PostMapping("/{id}/approve") public ResponseEntity<?> approve(@RequestHeader("X-Tenant-Id") UUID t, @PathVariable UUID id) { return ResponseEntity.ok(svc.approve(t, id)); }
    @GetMapping("/{id}/variance") public ResponseEntity<?> getVariance(@RequestHeader("X-Tenant-Id") UUID t, @PathVariable UUID id) { return ResponseEntity.ok(svc.getVariance(t, id)); }
    @GetMapping("/{id}") public ResponseEntity<?> getById(@RequestHeader("X-Tenant-Id") UUID t, @PathVariable UUID id) { return ResponseEntity.ok(svc.getById(t, id)); }
    @GetMapping public ResponseEntity<?> list(@RequestHeader("X-Tenant-Id") UUID t, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return ResponseEntity.ok(svc.list(t, org.springframework.data.domain.PageRequest.of(page, size))); }
}
