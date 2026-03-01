package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.AuditAction;
import com.bracit.fisprocess.domain.enums.AuditEntityType;
import com.bracit.fisprocess.dto.request.CreateAccountRequestDto;
import com.bracit.fisprocess.dto.request.UpdateAccountRequestDto;
import com.bracit.fisprocess.dto.response.AccountResponseDto;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.DuplicateAccountCodeException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.AccountService;
import com.bracit.fisprocess.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

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

    private static final Pattern ACCOUNT_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,50}$");

    private final AccountRepository accountRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final AuditService auditService;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public AccountResponseDto createAccount(UUID tenantId, CreateAccountRequestDto request, String performedBy) {
        validateTenantExists(tenantId);
        validateAccountCodeFormat(request.getCode());
        if (request.getParentAccountCode() != null) {
            validateAccountCodeFormat(request.getParentAccountCode());
        }
        validateAccountCodeUniqueness(tenantId, request.getCode());

        Account account = buildAccountFromRequest(tenantId, request);
        Account saved = accountRepository.save(account);
        AccountResponseDto response = toResponseDto(saved);

        auditService.logChange(
                tenantId,
                AuditEntityType.ACCOUNT,
                saved.getAccountId(),
                AuditAction.CREATED,
                null,
                Map.of(
                        "code", saved.getCode(),
                        "name", saved.getName(),
                        "accountType", saved.getAccountType().name(),
                        "currencyCode", saved.getCurrencyCode(),
                        "isContra", saved.isContra(),
                        "isActive", saved.isActive()),
                performedBy);

        log.info("Created account '{}' for tenant '{}'", saved.getCode(), tenantId);
        return response;
    }

    @Override
    public AccountResponseDto getAccountByCode(UUID tenantId, String accountCode) {
        validateTenantExists(tenantId);
        validateAccountCodeFormat(accountCode);

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
    public AccountResponseDto updateAccount(UUID tenantId, String accountCode, UpdateAccountRequestDto request,
            String performedBy) {
        validateTenantExists(tenantId);
        validateAccountCodeFormat(accountCode);

        Account account = findAccountOrThrow(tenantId, accountCode);
        Map<String, Object> oldValue = Map.of(
                "code", account.getCode(),
                "name", account.getName(),
                "isContra", account.isContra(),
                "isActive", account.isActive());
        applyUpdates(account, request);
        Account updated = accountRepository.save(account);
        AccountResponseDto response = toResponseDto(updated);

        AuditAction action = Boolean.FALSE.equals(request.getIsActive()) ? AuditAction.DEACTIVATED
                : AuditAction.UPDATED;
        auditService.logChange(
                tenantId,
                AuditEntityType.ACCOUNT,
                updated.getAccountId(),
                action,
                oldValue,
                Map.of(
                        "code", updated.getCode(),
                        "name", updated.getName(),
                        "isContra", updated.isContra(),
                        "isActive", updated.isActive()),
                performedBy);

        log.info("Updated account '{}' for tenant '{}'", accountCode, tenantId);
        return response;
    }

    @Override
    public AccountResponseDto getAggregatedBalance(UUID tenantId, String accountCode) {
        validateTenantExists(tenantId);
        validateAccountCodeFormat(accountCode);

        Account account = findAccountOrThrow(tenantId, accountCode);
        AccountResponseDto response = toResponseDto(account);
        Long aggregated = accountRepository.findAggregatedBalance(tenantId, accountCode);
        response.setAggregatedBalanceCents(aggregated != null ? aggregated : 0L);
        return response;
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

    private void validateAccountCodeFormat(String accountCode) {
        if (accountCode == null || !ACCOUNT_CODE_PATTERN.matcher(accountCode).matches()) {
            throw new IllegalArgumentException("Invalid account code format");
        }
    }

    private Account findAccountOrThrow(UUID tenantId, String accountCode) {
        return accountRepository.findByTenantIdAndCode(tenantId, accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));
    }

    private Account buildAccountFromRequest(UUID tenantId, CreateAccountRequestDto request) {
        Account account = modelMapper.map(request, Account.class);
        account.setTenantId(tenantId);

        if (request.getParentAccountCode() != null) {
            Account parent = findAccountOrThrow(tenantId, request.getParentAccountCode());
            account.setParentAccount(parent);
        }

        return account;
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
        AccountResponseDto response = modelMapper.map(account, AccountResponseDto.class);
        response.setAccountId(account.getAccountId());
        response.setCurrentBalanceCents(account.getCurrentBalance());
        response.setActive(account.isActive());
        response.setParentAccountCode(account.getParentAccount() != null ? account.getParentAccount().getCode() : null);
        response.setFormattedBalance(formatBalance(account.getCurrentBalance(), account.getCurrencyCode()));
        return response;
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
