package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.service.FixedAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/fixed-assets")
@RequiredArgsConstructor
public class FixedAssetController {

    private final FixedAssetService svc;

    // --- Category Management ---
    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestBody com.bracit.fisprocess.dto.request.CreateAssetCategoryRequestDto req) {
        return ResponseEntity.status(201).body(svc.createCategory(tenantId, req));
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<?> getCategory(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(svc.getCategoryById(tenantId, id));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> listCategories(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(svc.listCategories(tenantId, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}/schedule")
    public ResponseEntity<?> getDepreciationSchedule(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(svc.getDepreciationSchedule(tenantId, id));
    }

    // --- Asset Lifecycle ---
    @PostMapping
    public ResponseEntity<?> register(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @RequestBody com.bracit.fisprocess.dto.request.RegisterAssetRequestDto req) {
        return ResponseEntity.status(201).body(svc.register(tenantId, req, performedBy));
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<?> transfer(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam String newLocation,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        return ResponseEntity.ok(svc.transfer(tenantId, id, newLocation, performedBy));
    }

    @PostMapping("/{id}/revalue")
    public ResponseEntity<?> revalue(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam long newValue,
            @RequestParam String reason,
            @RequestParam LocalDate date,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        return ResponseEntity.ok(svc.revalue(tenantId, id, newValue, reason, date, performedBy));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(svc.getById(tenantId, id));
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(svc.list(tenantId, status, PageRequest.of(page, size)));
    }

    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<?> listByCategory(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(svc.listByCategory(tenantId, categoryId, PageRequest.of(page, size)));
    }

    // --- Depreciation ---
    @PostMapping("/depreciate")
    public ResponseEntity<?> runDepreciation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam String period,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        return ResponseEntity.ok(svc.runDepreciation(tenantId, period, performedBy));
    }

    @PostMapping("/depreciate/category/{categoryId}")
    public ResponseEntity<?> runDepreciationForCategory(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID categoryId,
            @RequestParam String period,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        return ResponseEntity.ok(svc.runDepreciationForCategory(tenantId, categoryId, period, performedBy));
    }

    @GetMapping("/depreciation-runs/{runId}")
    public ResponseEntity<?> getDepreciationRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID runId) {
        return ResponseEntity.ok(svc.getDepreciationRun(tenantId, runId));
    }

    @GetMapping("/depreciation-runs")
    public ResponseEntity<?> listDepreciationRuns(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String periodFrom,
            @RequestParam(required = false) String periodTo) {
        return ResponseEntity.ok(svc.listDepreciationRuns(tenantId, periodFrom, periodTo));
    }

    @PostMapping("/depreciation-runs/{runId}/reverse")
    public ResponseEntity<?> reverseDepreciation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID runId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        svc.reverseDepreciation(tenantId, runId, performedBy);
        return ResponseEntity.ok().build();
    }

    // --- Disposal ---
    @PostMapping("/{id}/dispose")
    public ResponseEntity<?> dispose(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody com.bracit.fisprocess.dto.request.AssetDisposalRequestDto req,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        return ResponseEntity.ok(svc.dispose(tenantId, id, req, performedBy));
    }

    @GetMapping("/disposals")
    public ResponseEntity<?> listDisposals(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(svc.listDisposals(tenantId,
                from != null ? from : LocalDate.now().minusMonths(12),
                to != null ? to : LocalDate.now()));
    }

    // --- Reporting ---
    @GetMapping("/register")
    public ResponseEntity<?> getAssetRegister(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(svc.getAssetRegister(tenantId, categoryId, status));
    }

    @GetMapping("/valuation")
    public ResponseEntity<?> getValuation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) LocalDate asOfDate) {
        return ResponseEntity.ok(svc.getValuation(tenantId,
                asOfDate != null ? asOfDate : LocalDate.now()));
    }
}