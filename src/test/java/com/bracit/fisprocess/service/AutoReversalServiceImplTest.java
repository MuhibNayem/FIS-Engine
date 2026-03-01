package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.impl.AutoReversalServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoReversalServiceImpl Unit Tests")
class AutoReversalServiceImplTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;
    @Mock
    private LedgerPersistenceService ledgerPersistenceService;
    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private AutoReversalServiceImpl autoReversalService;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    @DisplayName("should generate reversal for auto-reverse flagged JE on period open")
    void shouldGenerateReversalForAutoReverseJE() {
        UUID currentPeriodId = UUID.randomUUID();
        UUID priorPeriodId = UUID.randomUUID();
        UUID originalJeId = UUID.randomUUID();

        // Current period (newly opened)
        AccountingPeriod currentPeriod = AccountingPeriod.builder()
                .periodId(currentPeriodId)
                .tenantId(tenantId)
                .name("Feb 2025")
                .startDate(LocalDate.of(2025, 2, 1))
                .endDate(LocalDate.of(2025, 2, 28))
                .status(PeriodStatus.OPEN)
                .build();

        // Prior period
        AccountingPeriod priorPeriod = AccountingPeriod.builder()
                .periodId(priorPeriodId)
                .tenantId(tenantId)
                .name("Jan 2025")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 1, 31))
                .status(PeriodStatus.HARD_CLOSED)
                .build();

        when(accountingPeriodRepository.findById(currentPeriodId))
                .thenReturn(Optional.of(currentPeriod));
        when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(tenantId))
                .thenReturn(List.of(priorPeriod, currentPeriod));

        // Original auto-reverse JE from prior period
        Account accrualAcct = Account.builder()
                .accountId(UUID.randomUUID())
                .code("ACCRUAL-001")
                .build();
        Account expenseAcct = Account.builder()
                .accountId(UUID.randomUUID())
                .code("EXP-001")
                .build();

        JournalEntry originalJe = JournalEntry.builder()
                .id(originalJeId)
                .tenantId(tenantId)
                .eventId("ACCRUAL-EVT")
                .postedDate(LocalDate.of(2025, 1, 15))
                .status(JournalStatus.POSTED)
                .autoReverse(true)
                .transactionCurrency("USD")
                .baseCurrency("USD")
                .exchangeRate(BigDecimal.ONE)
                .createdBy("accountant")
                .createdAt(OffsetDateTime.now())
                .previousHash("abc")
                .hash("def")
                .fiscalYear(2025)
                .sequenceNumber(1L)
                .lines(new ArrayList<>())
                .build();

        JournalLine debitLine = JournalLine.builder()
                .account(expenseAcct)
                .amount(10000L)
                .baseAmount(10000L)
                .isCredit(false)
                .build();
        JournalLine creditLine = JournalLine.builder()
                .account(accrualAcct)
                .amount(10000L)
                .baseAmount(10000L)
                .isCredit(true)
                .build();
        originalJe.addLine(debitLine);
        originalJe.addLine(creditLine);

        when(journalEntryRepository.findAutoReverseEntries(
                tenantId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
                .thenReturn(List.of(originalJe));
        when(journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, originalJeId))
                .thenReturn(Optional.of(originalJe));
        when(journalEntryRepository.existsByTenantIdAndEventId(tenantId, "AUTO-REVERSE:" + originalJeId))
                .thenReturn(false);

        JournalEntry reversalJe = JournalEntry.builder().id(UUID.randomUUID()).build();
        when(ledgerPersistenceService.persist(any(DraftJournalEntry.class))).thenReturn(reversalJe);

        int count = autoReversalService.generateReversals(tenantId, currentPeriodId, "system");

        assertThat(count).isEqualTo(1);

        // Verify the reversal draft has flipped DR/CR
        ArgumentCaptor<DraftJournalEntry> captor = ArgumentCaptor.forClass(DraftJournalEntry.class);
        verify(ledgerPersistenceService).persist(captor.capture());
        DraftJournalEntry reversalDraft = captor.getValue();

        assertThat(reversalDraft.getPostedDate()).isEqualTo(LocalDate.of(2025, 2, 1)); // First day of new period
        assertThat(reversalDraft.getReversalOfId()).isEqualTo(originalJeId);
        assertThat(reversalDraft.isAutoReverse()).isFalse(); // Reversal itself is not auto-reverse
        assertThat(reversalDraft.getLines()).hasSize(2);
        // First line was debit (isCredit=false), reversal should be credit
        // (isCredit=true)
        assertThat(reversalDraft.getLines().get(0).isCredit()).isTrue();
        // Second line was credit (isCredit=true), reversal should be debit
        // (isCredit=false)
        assertThat(reversalDraft.getLines().get(1).isCredit()).isFalse();

        verify(outboxService).recordJournalPosted(eq(tenantId), any(), any(), any());
    }

    @Test
    @DisplayName("should not generate reversal for non-flagged JE")
    void shouldNotGenerateReversalForNonFlaggedJE() {
        UUID currentPeriodId = UUID.randomUUID();
        UUID priorPeriodId = UUID.randomUUID();

        AccountingPeriod currentPeriod = AccountingPeriod.builder()
                .periodId(currentPeriodId)
                .tenantId(tenantId)
                .name("Feb 2025")
                .startDate(LocalDate.of(2025, 2, 1))
                .endDate(LocalDate.of(2025, 2, 28))
                .status(PeriodStatus.OPEN)
                .build();

        AccountingPeriod priorPeriod = AccountingPeriod.builder()
                .periodId(priorPeriodId)
                .tenantId(tenantId)
                .name("Jan 2025")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 1, 31))
                .status(PeriodStatus.HARD_CLOSED)
                .build();

        when(accountingPeriodRepository.findById(currentPeriodId))
                .thenReturn(Optional.of(currentPeriod));
        when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(tenantId))
                .thenReturn(List.of(priorPeriod, currentPeriod));

        // No auto-reverse entries found (query only returns autoReverse=true)
        when(journalEntryRepository.findAutoReverseEntries(
                tenantId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
                .thenReturn(List.of());

        int count = autoReversalService.generateReversals(tenantId, currentPeriodId, "system");

        assertThat(count).isZero();
        verify(ledgerPersistenceService, never()).persist(any());
    }

    @Test
    @DisplayName("should skip when no prior period exists")
    void shouldSkipWhenNoPriorPeriod() {
        UUID currentPeriodId = UUID.randomUUID();

        AccountingPeriod currentPeriod = AccountingPeriod.builder()
                .periodId(currentPeriodId)
                .tenantId(tenantId)
                .name("Jan 2025")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 1, 31))
                .status(PeriodStatus.OPEN)
                .build();

        when(accountingPeriodRepository.findById(currentPeriodId))
                .thenReturn(Optional.of(currentPeriod));
        when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(tenantId))
                .thenReturn(List.of(currentPeriod)); // Only one period, so no prior

        int count = autoReversalService.generateReversals(tenantId, currentPeriodId, "system");

        assertThat(count).isZero();
        verify(ledgerPersistenceService, never()).persist(any());
    }
}
