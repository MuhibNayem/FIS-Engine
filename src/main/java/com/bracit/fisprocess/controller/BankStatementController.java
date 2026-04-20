package com.bracit.fisprocess.controller;
import com.bracit.fisprocess.dto.request.ImportBankStatementRequestDto;
import com.bracit.fisprocess.dto.response.BankStatementLineResponseDto;
import com.bracit.fisprocess.dto.response.BankStatementResponseDto;
import com.bracit.fisprocess.service.BankStatementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/v1/bank/statements") @RequiredArgsConstructor
public class BankStatementController {
    private final BankStatementService svc;
    @PostMapping("/import") public ResponseEntity<BankStatementResponseDto> importStatement(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @Valid @RequestBody ImportBankStatementRequestDto req) {
        return ResponseEntity.status(201).body(svc.importStatement(tenantId, req));
    }
    @GetMapping("/{id}") public ResponseEntity<BankStatementResponseDto> getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(svc.getById(tenantId, id));
    }
    @GetMapping public ResponseEntity<Page<BankStatementResponseDto>> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID bankAccountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(svc.list(tenantId, bankAccountId, PageRequest.of(page, size)));
    }
    @GetMapping("/{id}/lines") public ResponseEntity<List<BankStatementLineResponseDto>> getLines(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(svc.getLines(tenantId, id));
    }
}
