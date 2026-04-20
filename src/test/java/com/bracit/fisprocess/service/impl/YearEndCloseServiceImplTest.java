package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.YearEndCloseRequestDto;
import com.bracit.fisprocess.dto.response.YearEndCloseResponseDto;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.exception.YearEndCloseException;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("YearEndCloseServiceImpl Unit Tests")
class YearEndCloseServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private BusinessEntityRepository businessEntityRepository;
    @Mock private LedgerPersistenceService ledgerPersistenceService;
    @Mock private OutboxService outboxService;

    private YearEndCloseServiceImpl service;
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final int FISCAL_YEAR = 2025;

    @BeforeEach
    void setUp() {
        service = new YearEndCloseServiceImpl(
                accountRepository, accountingPeriodRepository, businessEntityRepository,
                ledgerPersistenceService, outboxService);
    }

    private BusinessEntity buildTenant() {
        return BusinessEntity.builder()
                .tenantId(TENANT_ID)
                .name("Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .build();
    }

    private Account buildRetainedEarnings() {
        return Account.builder()
                .accountId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .code("RE-001")
                .name("Retained Earnings")
                .accountType(AccountType.EQUITY)
                .currencyCode("USD")
                .currentBalance(50000L)
                .build();
    }

    private void stubTenantAndRetainedEarnings() {
        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.of(buildTenant()));
        when(accountRepository.findByTenantIdAndCode(TENANT_ID, "RE-001"))
                .thenReturn(Optional.of(buildRetainedEarnings()));
    }

    private void stubAllPeriodsHardClosed() {
        AccountingPeriod period = AccountingPeriod.builder()
                .periodId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .name("Q4 2025")
                .startDate(LocalDate.of(2025, 10, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .status(PeriodStatus.HARD_CLOSED)
                .build();
        when(accountingPeriodRepository.findOverlapping(TENANT_ID,
                LocalDate.of(FISCAL_YEAR, 1, 1), LocalDate.of(FISCAL_YEAR, 12, 31)))
                .thenReturn(List.of(period));
    }

    @Nested
    @DisplayName("performYearEndClose - happy path")
    class HappyPath {

        @Test
        @DisplayName("should close year with revenue and expense accounts")
        void shouldCloseYearWithPLAccounts() {
            stubTenantAndRetainedEarnings();
            stubAllPeriodsHardClosed();

            Account revenue = Account.builder()
                    .accountId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code("REV-001")
                    .name("Revenue")
                    .accountType(AccountType.REVENUE)
                    .currencyCode("USD")
                    .currentBalance(-10000L) // credit balance = negative
                    .build();
            Account expense = Account.builder()
                    .accountId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code("EXP-001")
                    .name("Expense")
                    .accountType(AccountType.EXPENSE)
                    .currencyCode("USD")
                    .currentBalance(6000L) // debit balance = positive
                    .build();
            when(accountRepository.findByTenantIdAndAccountTypeInAndCurrentBalanceNot(
                    TENANT_ID, List.of(AccountType.REVENUE, AccountType.EXPENSE), 0L))
                    .thenReturn(List.of(revenue, expense));

            JournalEntry closingEntry = JournalEntry.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .build();
            when(ledgerPersistenceService.persist(any())).thenReturn(closingEntry);

            YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                    .fiscalYear(FISCAL_YEAR)
                    .retainedEarningsAccountCode("RE-001")
                    .createdBy("admin")
                    .build();

            YearEndCloseResponseDto result = service.performYearEndClose(TENANT_ID, request);

            assertThat(result.getFiscalYear()).isEqualTo(FISCAL_YEAR);
            assertThat(result.getAccountsClosed()).isEqualTo(2);
            assertThat(result.getTotalRevenue()).isEqualTo(10000L);
            assertThat(result.getTotalExpenses()).isEqualTo(6000L);
            assertThat(result.getNetIncome()).isEqualTo(4000L);
            assertThat(result.getClosingJournalEntryId()).isEqualTo(closingEntry.getId());
            verify(outboxService).recordJournalPosted(eq(TENANT_ID), any(String.class), eq(closingEntry), isNull());
        }

        @Test
        @DisplayName("should return early when no P&L accounts with non-zero balances")
        void shouldReturnEarlyWhenNoPLAccounts() {
            stubTenantAndRetainedEarnings();
            stubAllPeriodsHardClosed();
            when(accountRepository.findByTenantIdAndAccountTypeInAndCurrentBalanceNot(
                    TENANT_ID, List.of(AccountType.REVENUE, AccountType.EXPENSE), 0L))
                    .thenReturn(List.of());

            YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                    .fiscalYear(FISCAL_YEAR)
                    .retainedEarningsAccountCode("RE-001")
                    .createdBy("admin")
                    .build();

            YearEndCloseResponseDto result = service.performYearEndClose(TENANT_ID, request);

            assertThat(result.getAccountsClosed()).isZero();
            assertThat(result.getNetIncome()).isZero();
            assertThat(result.getMessage()).contains("Nothing to close");
        }
    }

    @Nested
    @DisplayName("performYearEndClose - validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("should throw TenantNotFoundException when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                    .fiscalYear(FISCAL_YEAR)
                    .retainedEarningsAccountCode("RE-001")
                    .createdBy("admin")
                    .build();

            assertThatThrownBy(() -> service.performYearEndClose(TENANT_ID, request))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when retained earnings not found")
        void shouldThrowWhenRetainedEarningsNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(buildTenant()));
            when(accountRepository.findByTenantIdAndCode(TENANT_ID, "RE-001"))
                    .thenReturn(Optional.empty());

            YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                    .fiscalYear(FISCAL_YEAR)
                    .retainedEarningsAccountCode("RE-001")
                    .createdBy("admin")
                    .build();

            assertThatThrownBy(() -> service.performYearEndClose(TENANT_ID, request))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw YearEndCloseException when retained earnings is not EQUITY type")
        void shouldThrowWhenRetainedEarningsNotEquity() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(buildTenant()));
            Account nonEquity = Account.builder()
                    .accountId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code("RE-001")
                    .accountType(AccountType.ASSET)
                    .build();
            when(accountRepository.findByTenantIdAndCode(TENANT_ID, "RE-001"))
                    .thenReturn(Optional.of(nonEquity));

            YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                    .fiscalYear(FISCAL_YEAR)
                    .retainedEarningsAccountCode("RE-001")
                    .createdBy("admin")
                    .build();

            assertThatThrownBy(() -> service.performYearEndClose(TENANT_ID, request))
                    .isInstanceOf(YearEndCloseException.class)
                    .hasMessageContaining("must be of type EQUITY");
        }

        @Test
        @DisplayName("should throw when periods are not HARD_CLOSED")
        void shouldThrowWhenPeriodsNotHardClosed() {
            stubTenantAndRetainedEarnings();
            AccountingPeriod openPeriod = AccountingPeriod.builder()
                    .periodId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .name("Q4 2025")
                    .status(PeriodStatus.OPEN)
                    .build();
            when(accountingPeriodRepository.findOverlapping(TENANT_ID,
                    LocalDate.of(FISCAL_YEAR, 1, 1), LocalDate.of(FISCAL_YEAR, 12, 31)))
                    .thenReturn(List.of(openPeriod));

            YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                    .fiscalYear(FISCAL_YEAR)
                    .retainedEarningsAccountCode("RE-001")
                    .createdBy("admin")
                    .build();

            assertThatThrownBy(() -> service.performYearEndClose(TENANT_ID, request))
                    .isInstanceOf(YearEndCloseException.class)
                    .hasMessageContaining("not HARD_CLOSED");
        }

        @Test
        @DisplayName("should throw when no periods found")
        void shouldThrowWhenNoPeriods() {
            stubTenantAndRetainedEarnings();
            when(accountingPeriodRepository.findOverlapping(TENANT_ID,
                    LocalDate.of(FISCAL_YEAR, 1, 1), LocalDate.of(FISCAL_YEAR, 12, 31)))
                    .thenReturn(List.of());

            YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                    .fiscalYear(FISCAL_YEAR)
                    .retainedEarningsAccountCode("RE-001")
                    .createdBy("admin")
                    .build();

            assertThatThrownBy(() -> service.performYearEndClose(TENANT_ID, request))
                    .isInstanceOf(YearEndCloseException.class)
                    .hasMessageContaining("No accounting periods found");
        }
    }
}
