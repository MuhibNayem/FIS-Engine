package com.bracit.fisprocess.controller;
import com.bracit.fisprocess.domain.enums.BankAccountStatus;
import com.bracit.fisprocess.dto.request.RegisterBankAccountRequestDto;
import com.bracit.fisprocess.dto.response.BankAccountResponseDto;
import com.bracit.fisprocess.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/bank/accounts") @RequiredArgsConstructor
public class BankAccountController {
    private final BankAccountService svc;
    @PostMapping public ResponseEntity<BankAccountResponseDto> register(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody RegisterBankAccountRequestDto req) {
        return ResponseEntity.status(201).body(svc.register(tenantId, req, performedBy));
    }
    @PostMapping("/{id}/close") public ResponseEntity<BankAccountResponseDto> close(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        return ResponseEntity.ok(svc.close(tenantId, id, performedBy));
    }
    @GetMapping("/{id}") public ResponseEntity<BankAccountResponseDto> getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(svc.getById(tenantId, id));
    }
    @GetMapping public ResponseEntity<Page<BankAccountResponseDto>> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable BankAccountStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(svc.list(tenantId, status, PageRequest.of(page, size)));
    }
}
