package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.dto.request.CreateAccountRequestDto;
import com.bracit.fisprocess.dto.request.UpdateAccountRequestDto;
import com.bracit.fisprocess.dto.response.AccountResponseDto;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.DuplicateAccountCodeException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * Implementation of {@link AccountService} responsible for Account CRUD
 * operations.
 * <p>
 * Validates tenant existence, account code uniqueness, and parent account
 * resolution before persisting. All reads and writes are tenant-scoped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final BusinessEntityRepository businessEntityRepository;

    @Override
    @Transactional
    public AccountResponseDto createAccount(UUID tenantId, CreateAccountRequestDto request) {
        validateTenantExists(tenantId);
        validateAccountCodeUniqueness(tenantId, request.getCode());

        Account account = buildAccountFromRequest(tenantId, request);
        Account saved = accountRepository.save(account);

        log.info("Created account '{}' for tenant '{}'", saved.getCode(), tenantId);
        return toResponseDto(saved);
    }

    @Override
    public AccountResponseDto getAccountByCode(UUID tenantId, String accountCode) {
        validateTenantExists(tenantId);

        Account account = findAccountOrThrow(tenantId, accountCode);
        return toResponseDto(account);
    }

    @Override
    public Page<AccountResponseDto> listAccounts(
            UUID tenantId,
            @Nullable AccountType accountType,
            @Nullable Boolean isActive,
            Pageable pageable) {
        validateTenantExists(tenantId);

        return accountRepository
                .findByTenantIdWithFilters(tenantId, accountType, isActive, pageable)
                .map(this::toResponseDto);
    }

    @Override
    @Transactional
    public AccountResponseDto updateAccount(UUID tenantId, String accountCode, UpdateAccountRequestDto request) {
        validateTenantExists(tenantId);

        Account account = findAccountOrThrow(tenantId, accountCode);
        applyUpdates(account, request);
        Account updated = accountRepository.save(account);

        log.info("Updated account '{}' for tenant '{}'", accountCode, tenantId);
        return toResponseDto(updated);
    }

    // --- Private Helper Methods ---

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private void validateAccountCodeUniqueness(UUID tenantId, String code) {
        if (accountRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new DuplicateAccountCodeException(code);
        }
    }

    private Account findAccountOrThrow(UUID tenantId, String accountCode) {
        return accountRepository.findByTenantIdAndCode(tenantId, accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));
    }

    private Account buildAccountFromRequest(UUID tenantId, CreateAccountRequestDto request) {
        Account.AccountBuilder builder = Account.builder()
                .tenantId(tenantId)
                .code(request.getCode())
                .name(request.getName())
                .accountType(request.getAccountType())
                .currencyCode(request.getCurrencyCode());

        if (request.getParentAccountCode() != null) {
            Account parent = findAccountOrThrow(tenantId, request.getParentAccountCode());
            builder.parentAccount(parent);
        }

        return builder.build();
    }

    private void applyUpdates(Account account, UpdateAccountRequestDto request) {
        if (request.getName() != null) {
            account.setName(request.getName());
        }
        if (request.getIsActive() != null) {
            account.setActive(request.getIsActive());
        }
    }

    private AccountResponseDto toResponseDto(Account account) {
        @Nullable
        String parentCode = account.getParentAccount() != null
                ? account.getParentAccount().getCode()
                : null;

        return AccountResponseDto.builder()
                .accountId(account.getAccountId())
                .code(account.getCode())
                .name(account.getName())
                .accountType(account.getAccountType())
                .currencyCode(account.getCurrencyCode())
                .currentBalanceCents(account.getCurrentBalance())
                .formattedBalance(formatBalance(account.getCurrentBalance(), account.getCurrencyCode()))
                .isActive(account.isActive())
                .parentAccountCode(parentCode)
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private String formatBalance(Long balanceCents, String currencyCode) {
        try {
            Currency currency = Currency.getInstance(currencyCode);
            int fractionDigits = currency.getDefaultFractionDigits();
            double amount = balanceCents / Math.pow(10, fractionDigits);
            NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.US);
            formatter.setCurrency(currency);
            return formatter.format(amount);
        } catch (IllegalArgumentException e) {
            // Fallback for unknown currencies
            return String.format("%.2f %s", balanceCents / 100.0, currencyCode);
        }
    }
}
