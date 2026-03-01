package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.YearEndCloseRequestDto;
import com.bracit.fisprocess.dto.response.YearEndCloseResponseDto;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.YearEndCloseException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.impl.YearEndCloseServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("YearEndCloseServiceImpl Unit Tests")
class YearEndCloseServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private LedgerPersistenceService ledgerPersistenceService;
    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private YearEndCloseServiceImpl yearEndCloseService;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    @DisplayName("should reject year-end close when periods are not all HARD_CLOSED")
    void shouldRejectWhenPeriodsNotHardClosed() {
        YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                .fiscalYear(2025)
                .retainedEarningsAccountCode("RE-001")
                .createdBy("admin")
                .build();

        // Tenant exists
        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        // Retained earnings account exists and is EQUITY
        when(accountRepository.findByTenantIdAndCode(tenantId, "RE-001"))
                .thenReturn(Optional.of(Account.builder()
                        .accountId(UUID.randomUUID())
                        .code("RE-001")
                        .accountType(AccountType.EQUITY)
                        .build()));

        // One period is still OPEN
        AccountingPeriod openPeriod = AccountingPeriod.builder()
                .periodId(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Q1 2025")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 3, 31))
                .status(PeriodStatus.OPEN)
                .build();
        AccountingPeriod closedPeriod = AccountingPeriod.builder()
                .periodId(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Q2 2025")
                .startDate(LocalDate.of(2025, 4, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(PeriodStatus.HARD_CLOSED)
                .build();

        when(accountingPeriodRepository.findOverlapping(tenantId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .thenReturn(List.of(openPeriod, closedPeriod));

        assertThatThrownBy(() -> yearEndCloseService.performYearEndClose(tenantId, request))
                .isInstanceOf(YearEndCloseException.class)
                .hasMessageContaining("not HARD_CLOSED");

        verify(ledgerPersistenceService, never()).persist(any());
    }

    @Test
    @DisplayName("should reject when retained earnings account is not EQUITY type")
    void shouldRejectWhenRetainedEarningsNotEquity() {
        YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                .fiscalYear(2025)
                .retainedEarningsAccountCode("REVENUE-001")
                .createdBy("admin")
                .build();

        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        when(accountRepository.findByTenantIdAndCode(tenantId, "REVENUE-001"))
                .thenReturn(Optional.of(Account.builder()
                        .accountId(UUID.randomUUID())
                        .code("REVENUE-001")
                        .accountType(AccountType.REVENUE)
                        .build()));

        assertThatThrownBy(() -> yearEndCloseService.performYearEndClose(tenantId, request))
                .isInstanceOf(YearEndCloseException.class)
                .hasMessageContaining("must be of type EQUITY");

        verify(ledgerPersistenceService, never()).persist(any());
    }

    @Test
    @DisplayName("should reject when retained earnings account does not exist")
    void shouldRejectWhenRetainedEarningsNotFound() {
        YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                .fiscalYear(2025)
                .retainedEarningsAccountCode("MISSING")
                .createdBy("admin")
                .build();

        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        when(accountRepository.findByTenantIdAndCode(tenantId, "MISSING"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> yearEndCloseService.performYearEndClose(tenantId, request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("should return no-op response when no P&L accounts have balances")
    void shouldHandleNoPandLBalances() {
        YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                .fiscalYear(2025)
                .retainedEarningsAccountCode("RE-001")
                .createdBy("admin")
                .build();

        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        when(accountRepository.findByTenantIdAndCode(tenantId, "RE-001"))
                .thenReturn(Optional.of(Account.builder()
                        .accountId(UUID.randomUUID())
                        .code("RE-001")
                        .accountType(AccountType.EQUITY)
                        .build()));

        // All periods HARD_CLOSED
        when(accountingPeriodRepository.findOverlapping(tenantId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .thenReturn(List.of(AccountingPeriod.builder()
                        .periodId(UUID.randomUUID())
                        .tenantId(tenantId)
                        .name("FY2025")
                        .startDate(LocalDate.of(2025, 1, 1))
                        .endDate(LocalDate.of(2025, 12, 31))
                        .status(PeriodStatus.HARD_CLOSED)
                        .build()));

        // No P&L accounts with balances
        when(accountRepository.findByTenantIdAndAccountTypeInAndCurrentBalanceNot(
                tenantId, List.of(AccountType.REVENUE, AccountType.EXPENSE), 0L))
                .thenReturn(List.of());

        YearEndCloseResponseDto response = yearEndCloseService.performYearEndClose(tenantId, request);

        assertThat(response.getNetIncome()).isZero();
        assertThat(response.getAccountsClosed()).isZero();
        assertThat(response.getMessage()).contains("Nothing to close");
        verify(ledgerPersistenceService, never()).persist(any());
    }

    @Test
    @DisplayName("should generate closing JE with correct P&L totals")
    void shouldGenerateClosingJournalEntry() {
        YearEndCloseRequestDto request = YearEndCloseRequestDto.builder()
                .fiscalYear(2025)
                .retainedEarningsAccountCode("RE-001")
                .createdBy("admin")
                .build();

        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder()
                        .tenantId(tenantId)
                        .baseCurrency("USD")
                        .build()));

        when(accountRepository.findByTenantIdAndCode(tenantId, "RE-001"))
                .thenReturn(Optional.of(Account.builder()
                        .accountId(UUID.randomUUID())
                        .code("RE-001")
                        .accountType(AccountType.EQUITY)
                        .build()));

        when(accountingPeriodRepository.findOverlapping(tenantId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .thenReturn(List.of(AccountingPeriod.builder()
                        .periodId(UUID.randomUUID())
                        .tenantId(tenantId)
                        .name("FY2025")
                        .startDate(LocalDate.of(2025, 1, 1))
                        .endDate(LocalDate.of(2025, 12, 31))
                        .status(PeriodStatus.HARD_CLOSED)
                        .build()));

        // Revenue account with -50000 balance (credit-normal)
        Account revenueAcct = Account.builder()
                .accountId(UUID.randomUUID())
                .code("REV-001")
                .accountType(AccountType.REVENUE)
                .currentBalance(-50000L)
                .build();
        // Expense account with 30000 balance (debit-normal)
        Account expenseAcct = Account.builder()
                .accountId(UUID.randomUUID())
                .code("EXP-001")
                .accountType(AccountType.EXPENSE)
                .currentBalance(30000L)
                .build();

        when(accountRepository.findByTenantIdAndAccountTypeInAndCurrentBalanceNot(
                tenantId, List.of(AccountType.REVENUE, AccountType.EXPENSE), 0L))
                .thenReturn(List.of(revenueAcct, expenseAcct));

        UUID closingJeId = UUID.randomUUID();
        JournalEntry closingJe = JournalEntry.builder().id(closingJeId).build();
        when(ledgerPersistenceService.persist(any())).thenReturn(closingJe);

        YearEndCloseResponseDto response = yearEndCloseService.performYearEndClose(tenantId, request);

        assertThat(response.getFiscalYear()).isEqualTo(2025);
        assertThat(response.getClosingJournalEntryId()).isEqualTo(closingJeId);
        assertThat(response.getTotalRevenue()).isEqualTo(50000L);
        assertThat(response.getTotalExpenses()).isEqualTo(30000L);
        assertThat(response.getNetIncome()).isEqualTo(20000L); // Profit
        assertThat(response.getAccountsClosed()).isEqualTo(2);
        verify(ledgerPersistenceService).persist(any());
        verify(outboxService).recordJournalPosted(any(), any(), any(), any());
    }
}
