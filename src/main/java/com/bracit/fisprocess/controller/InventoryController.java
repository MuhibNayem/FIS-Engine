package com.bracit.fisprocess.controller;
import com.bracit.fisprocess.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/inventory") @RequiredArgsConstructor
public class InventoryController {
    private final InventoryService svc;
    @PostMapping("/warehouses") public ResponseEntity<?> createWarehouse(@RequestHeader("X-Tenant-Id") UUID t, @RequestBody com.bracit.fisprocess.dto.request.CreateWarehouseRequestDto req) { return ResponseEntity.status(201).body(svc.createWarehouse(t, req)); }
    @PostMapping("/items") public ResponseEntity<?> createItem(@RequestHeader("X-Tenant-Id") UUID t, @RequestBody com.bracit.fisprocess.dto.request.CreateInventoryItemRequestDto req) { return ResponseEntity.status(201).body(svc.createItem(t, req)); }
    @PostMapping("/movements") public ResponseEntity<?> recordMovement(@RequestHeader("X-Tenant-Id") UUID t, @RequestBody com.bracit.fisprocess.dto.request.RecordInventoryMovementRequestDto req) { return ResponseEntity.status(201).body(svc.recordMovement(t, req)); }
    @GetMapping("/valuation") public ResponseEntity<?> getValuation(@RequestHeader("X-Tenant-Id") UUID t, @RequestParam String period) { return ResponseEntity.ok(svc.getValuation(t, period)); }
    @GetMapping("/warehouses/{id}") public ResponseEntity<?> getWarehouse(@RequestHeader("X-Tenant-Id") UUID t, @PathVariable UUID id) { return ResponseEntity.ok(svc.getWarehouse(t, id)); }
    @GetMapping("/items/{id}") public ResponseEntity<?> getItem(@RequestHeader("X-Tenant-Id") UUID t, @PathVariable UUID id) { return ResponseEntity.ok(svc.getItem(t, id)); }
}
