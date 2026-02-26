package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.dto.request.CreateAccountRequestDto;
import com.bracit.fisprocess.dto.request.UpdateAccountRequestDto;
import com.bracit.fisprocess.dto.response.AccountResponseDto;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.DuplicateAccountCodeException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.AuditService;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountServiceImpl Unit Tests")
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private AuditService auditService;

    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private AccountServiceImpl accountService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String ACCOUNT_CODE = "1000-CASH";

    private BusinessEntity activeTenant;

    @BeforeEach
    void setUp() {
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true);

        activeTenant = BusinessEntity.builder()
                .tenantId(TENANT_ID)
                .name("Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private Account buildAccount() {
        return Account.builder()
                .accountId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .code(ACCOUNT_CODE)
                .name("Cash and Cash Equivalents")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .currentBalance(0L)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private CreateAccountRequestDto buildCreateRequest() {
        return CreateAccountRequestDto.builder()
                .code(ACCOUNT_CODE)
                .name("Cash and Cash Equivalents")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .build();
    }

    // --- createAccount Tests ---

    @Nested
    @DisplayName("createAccount")
    class CreateAccountTests {

        @Test
        @DisplayName("should create account successfully with balance = 0")
        void shouldCreateAccountSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.existsByTenantIdAndCode(TENANT_ID, ACCOUNT_CODE))
                    .thenReturn(false);

            Account savedAccount = buildAccount();
            when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

            AccountResponseDto result = accountService.createAccount(TENANT_ID, buildCreateRequest());

            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo(ACCOUNT_CODE);
            assertThat(result.getAccountType()).isEqualTo(AccountType.ASSET);
            assertThat(result.getCurrentBalanceCents()).isZero();
            assertThat(result.isActive()).isTrue();
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("should throw DuplicateAccountCodeException when code already exists")
        void shouldThrowWhenDuplicateCode() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.existsByTenantIdAndCode(TENANT_ID, ACCOUNT_CODE))
                    .thenReturn(true);

            assertThatThrownBy(() -> accountService.createAccount(TENANT_ID, buildCreateRequest()))
                    .isInstanceOf(DuplicateAccountCodeException.class);
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw TenantNotFoundException when tenant is invalid")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.createAccount(TENANT_ID, buildCreateRequest()))
                    .isInstanceOf(TenantNotFoundException.class);
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("should resolve parent account when parentAccountCode is provided")
        void shouldResolveParentAccount() {
            String parentCode = "1000-PARENT";
            Account parentAccount = Account.builder()
                    .accountId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code(parentCode)
                    .name("Parent")
                    .accountType(AccountType.ASSET)
                    .currencyCode("USD")
                    .currentBalance(0L)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                    .code(ACCOUNT_CODE)
                    .name("Cash")
                    .accountType(AccountType.ASSET)
                    .currencyCode("USD")
                    .parentAccountCode(parentCode)
                    .build();

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.existsByTenantIdAndCode(TENANT_ID, ACCOUNT_CODE))
                    .thenReturn(false);
            when(accountRepository.findByTenantIdAndCode(TENANT_ID, parentCode))
                    .thenReturn(Optional.of(parentAccount));

            Account savedAccount = buildAccount();
            savedAccount.setParentAccount(parentAccount);
            when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

            AccountResponseDto result = accountService.createAccount(TENANT_ID, request);

            assertThat(result.getParentAccountCode()).isEqualTo(parentCode);
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when parent account doesn't exist")
        void shouldThrowWhenParentNotFound() {
            CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                    .code(ACCOUNT_CODE)
                    .name("Cash")
                    .accountType(AccountType.ASSET)
                    .currencyCode("USD")
                    .parentAccountCode("NONEXISTENT")
                    .build();

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.existsByTenantIdAndCode(TENANT_ID, ACCOUNT_CODE))
                    .thenReturn(false);
            when(accountRepository.findByTenantIdAndCode(TENANT_ID, "NONEXISTENT"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.createAccount(TENANT_ID, request))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    // --- getAccountByCode Tests ---

    @Nested
    @DisplayName("getAccountByCode")
    class GetAccountByCodeTests {

        @Test
        @DisplayName("should return account when found")
        void shouldReturnAccountWhenFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.findByTenantIdAndCode(TENANT_ID, ACCOUNT_CODE))
                    .thenReturn(Optional.of(buildAccount()));

            AccountResponseDto result = accountService.getAccountByCode(TENANT_ID, ACCOUNT_CODE);

            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo(ACCOUNT_CODE);
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.findByTenantIdAndCode(TENANT_ID, ACCOUNT_CODE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountByCode(TENANT_ID, ACCOUNT_CODE))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    // --- updateAccount Tests ---

    @Nested
    @DisplayName("updateAccount")
    class UpdateAccountTests {

        @Test
        @DisplayName("should deactivate account")
        void shouldDeactivateAccount() {
            Account account = buildAccount();
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.findByTenantIdAndCode(TENANT_ID, ACCOUNT_CODE))
                    .thenReturn(Optional.of(account));

            Account updatedAccount = buildAccount();
            updatedAccount.setActive(false);
            when(accountRepository.save(any(Account.class))).thenReturn(updatedAccount);

            UpdateAccountRequestDto request = UpdateAccountRequestDto.builder()
                    .isActive(false)
                    .build();

            AccountResponseDto result = accountService.updateAccount(TENANT_ID, ACCOUNT_CODE, request);

            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("should update account name")
        void shouldUpdateName() {
            Account account = buildAccount();
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.findByTenantIdAndCode(TENANT_ID, ACCOUNT_CODE))
                    .thenReturn(Optional.of(account));

            Account updatedAccount = buildAccount();
            updatedAccount.setName("Updated Name");
            when(accountRepository.save(any(Account.class))).thenReturn(updatedAccount);

            UpdateAccountRequestDto request = UpdateAccountRequestDto.builder()
                    .name("Updated Name")
                    .build();

            AccountResponseDto result = accountService.updateAccount(TENANT_ID, ACCOUNT_CODE, request);

            assertThat(result.getName()).isEqualTo("Updated Name");
        }
    }

    // --- listAccounts Tests ---

    @Nested
    @DisplayName("listAccounts")
    class ListAccountsTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            List<Account> accounts = List.of(buildAccount());
            Page<Account> page = new PageImpl<>(accounts, pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(accountRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<AccountResponseDto> result = accountService.listAccounts(
                    TENANT_ID, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
