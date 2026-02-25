package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.dto.request.CreateAccountRequestDto;
import com.bracit.fisprocess.dto.request.UpdateAccountRequestDto;
import com.bracit.fisprocess.dto.response.AccountResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for Account management operations.
 * <p>
 * All operations are tenant-scoped via {@code tenantId}.
 */
public interface AccountService {

    /**
     * Creates a new account in the Chart of Accounts for the specified tenant.
     *
     * @param tenantId the tenant UUID from the X-Tenant-Id header
     * @param request  the account creation details
     * @return the created account response
     */
    AccountResponseDto createAccount(UUID tenantId, CreateAccountRequestDto request);

    /**
     * Retrieves an account by its unique code within a tenant.
     *
     * @param tenantId    the tenant UUID
     * @param accountCode the account code
     * @return the account response
     */
    AccountResponseDto getAccountByCode(UUID tenantId, String accountCode);

    /**
     * Lists accounts for a tenant with optional filters and pagination.
     *
     * @param tenantId    the tenant UUID
     * @param accountType optional filter by account type
     * @param isActive    optional filter by active status
     * @param pageable    pagination parameters
     * @return a page of account responses
     */
    Page<AccountResponseDto> listAccounts(
            UUID tenantId,
            @Nullable AccountType accountType,
            @Nullable Boolean isActive,
            Pageable pageable);

    /**
     * Updates an existing account's name and/or active status.
     *
     * @param tenantId    the tenant UUID
     * @param accountCode the account code to update
     * @param request     the update details (nullable fields for partial update)
     * @return the updated account response
     */
    AccountResponseDto updateAccount(UUID tenantId, String accountCode, UpdateAccountRequestDto request);
}
