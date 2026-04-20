package com.bracit.fisprocess.controller;
import com.bracit.fisprocess.dto.request.MatchLineRequestDto;
import com.bracit.fisprocess.dto.request.StartReconciliationRequestDto;
import com.bracit.fisprocess.dto.response.OutstandingItemsReportDto;
import com.bracit.fisprocess.dto.response.ReconciliationResponseDto;
import com.bracit.fisprocess.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/v1/bank/reconciliations") @RequiredArgsConstructor
public class ReconciliationController {
    private final ReconciliationService svc;
    @PostMapping("/start") public ResponseEntity<ReconciliationResponseDto> start(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody StartReconciliationRequestDto req) {
        return ResponseEntity.status(201).body(svc.start(tenantId, req, performedBy));
    }
    @PostMapping("/{id}/auto-match") public ResponseEntity<List<ReconciliationService.MatchResult>> autoMatch(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(svc.autoMatch(tenantId, id));
    }
    @PostMapping("/{id}/match") public ResponseEntity<ReconciliationService.MatchResult> match(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id,
            @Valid @RequestBody MatchLineRequestDto req) {
        return ResponseEntity.ok(svc.manualMatch(tenantId, id, req));
    }
    @PostMapping("/matches/{matchId}/unmatch") public ResponseEntity<ReconciliationService.MatchResult> unmatch(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID matchId) {
        return ResponseEntity.ok(svc.unmatch(tenantId, matchId));
    }
    @PostMapping("/{id}/complete") public ResponseEntity<ReconciliationResponseDto> complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        return ResponseEntity.ok(svc.complete(tenantId, id, performedBy));
    }
    @GetMapping("/{id}") public ResponseEntity<ReconciliationResponseDto> getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(svc.getById(tenantId, id));
    }
    @GetMapping public ResponseEntity<Page<ReconciliationResponseDto>> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID bankAccountId,
            @RequestParam(required = false) @Nullable String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(svc.list(tenantId, bankAccountId, status, PageRequest.of(page, size)));
    }
    @GetMapping("/reports/outstanding-items") public ResponseEntity<OutstandingItemsReportDto> outstandingItems(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID bankAccountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(svc.getOutstandingItems(tenantId, bankAccountId, asOfDate));
    }
}
