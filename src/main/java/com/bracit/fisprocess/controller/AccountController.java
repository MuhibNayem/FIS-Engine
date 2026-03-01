package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.dto.request.CreateAccountRequestDto;
import com.bracit.fisprocess.dto.request.UpdateAccountRequestDto;
import com.bracit.fisprocess.dto.response.AccountResponseDto;
import com.bracit.fisprocess.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for Account management within the Chart of Accounts.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific Business Entity (tenant).
 */
@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Creates a new account in the Chart of Accounts.
     *
     * @param tenantId the tenant UUID from X-Tenant-Id header
     * @param request  the account creation details
     * @return 201 Created with the new account details
     */
    @PostMapping
    public ResponseEntity<AccountResponseDto> createAccount(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody CreateAccountRequestDto request) {
        AccountResponseDto response = accountService.createAccount(tenantId, request, performedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves an account by its code.
     *
     * @param tenantId    the tenant UUID
     * @param accountCode the account code
     * @return 200 OK with account details
     */
    @GetMapping("/{accountCode}")
    public ResponseEntity<AccountResponseDto> getAccountByCode(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String accountCode) {
        AccountResponseDto response = accountService.getAccountByCode(tenantId, accountCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists accounts for a tenant with optional filters and pagination.
     *
     * @param tenantId    the tenant UUID
     * @param accountType optional filter by account type
     * @param isActive    optional filter by active status
     * @param pageable    pagination parameters (default: page=0, size=20)
     * @return 200 OK with paginated account list
     */
    @GetMapping
    public ResponseEntity<Page<AccountResponseDto>> listAccounts(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable AccountType accountType,
            @RequestParam(required = false) @Nullable Boolean isActive,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AccountResponseDto> response = accountService.listAccounts(
                tenantId, accountType, isActive, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an account's name and/or active status.
     *
     * @param tenantId    the tenant UUID
     * @param accountCode the account code to update
     * @param request     the update details
     * @return 200 OK with updated account details
     */
    @PatchMapping("/{accountCode}")
    public ResponseEntity<AccountResponseDto> updateAccount(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @PathVariable String accountCode,
            @Valid @RequestBody UpdateAccountRequestDto request) {
        AccountResponseDto response = accountService.updateAccount(tenantId, accountCode, request, performedBy);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves an account's aggregated balance — the sum of its own balance
     * plus all descendant account balances in the hierarchy.
     *
     * @param tenantId    the tenant UUID
     * @param accountCode the account code
     * @return 200 OK with account details including aggregatedBalanceCents
     */
    @GetMapping("/{accountCode}/aggregated-balance")
    public ResponseEntity<AccountResponseDto> getAggregatedBalance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String accountCode) {
        AccountResponseDto response = accountService.getAggregatedBalance(tenantId, accountCode);
        return ResponseEntity.ok(response);
    }
}
