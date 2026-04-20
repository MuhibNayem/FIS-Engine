package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.exception.AccountingPeriodNotFoundException;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoReversalServiceImpl Unit Tests")
class AutoReversalServiceImplTest {

    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private LedgerPersistenceService ledgerPersistenceService;
    @Mock private OutboxService outboxService;

    private AutoReversalServiceImpl service;
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AutoReversalServiceImpl(
                journalEntryRepository, accountingPeriodRepository,
                ledgerPersistenceService, outboxService);
    }

    private AccountingPeriod buildCurrentPeriod() {
        return AccountingPeriod.builder()
                .periodId(PERIOD_ID)
                .tenantId(TENANT_ID)
                .name("Jan 2026")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 31))
                .build();
    }

    private AccountingPeriod buildPriorPeriod() {
        return AccountingPeriod.builder()
                .periodId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .name("Dec 2025")
                .startDate(LocalDate.of(2025, 12, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .build();
    }

    @Nested
    @DisplayName("generateReversals - happy path")
    class HappyPath {

        @Test
        @DisplayName("should generate reversals for auto-reverse entries from prior period")
        void shouldGenerateReversals() {
            when(accountingPeriodRepository.findById(PERIOD_ID))
                    .thenReturn(Optional.of(buildCurrentPeriod()));
            AccountingPeriod prior = buildPriorPeriod();
            when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(TENANT_ID))
                    .thenReturn(List.of(prior, buildCurrentPeriod()));

            UUID originalJeId = UUID.randomUUID();
            JournalEntry original = JournalEntry.builder()
                    .id(originalJeId)
                    .tenantId(TENANT_ID)
                    .autoReverse(true)
                    .transactionCurrency("USD")
                    .baseCurrency("USD")
                    .exchangeRate(BigDecimal.ONE)
                    .referenceId("ACCRUAL-001")
                    .build();
            when(journalEntryRepository.findAutoReverseEntries(TENANT_ID, prior.getStartDate(), prior.getEndDate()))
                    .thenReturn(List.of(original));

            JournalEntry withLines = JournalEntry.builder()
                    .id(originalJeId)
                    .tenantId(TENANT_ID)
                    .autoReverse(true)
                    .transactionCurrency("USD")
                    .baseCurrency("USD")
                    .exchangeRate(BigDecimal.ONE)
                    .referenceId("ACCRUAL-001")
                    .build();
            withLines.addLine(JournalLine.builder()
                    .account(com.bracit.fisprocess.domain.entity.Account.builder()
                            .code("ACCRUED-EXP")
                            .accountType(AccountType.EXPENSE)
                            .build())
                    .amount(5000L)
                    .baseAmount(5000L)
                    .isCredit(false)
                    .build());
            when(journalEntryRepository.findWithLinesByTenantIdAndId(TENANT_ID, originalJeId))
                    .thenReturn(Optional.of(withLines));
            when(journalEntryRepository.existsByTenantIdAndEventId(TENANT_ID, "AUTO-REVERSE:" + originalJeId))
                    .thenReturn(false);

            DraftJournalEntry reversalDraft = DraftJournalEntry.builder().build();
            JournalEntry reversal = JournalEntry.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .build();
            when(ledgerPersistenceService.persist(any())).thenReturn(reversal);

            int count = service.generateReversals(TENANT_ID, PERIOD_ID, "system");

            assertThat(count).isEqualTo(1);
            ArgumentCaptor<DraftJournalEntry> captor = ArgumentCaptor.forClass(DraftJournalEntry.class);
            verify(ledgerPersistenceService).persist(captor.capture());
            DraftJournalEntry captured = captor.getValue();
            assertThat(captured.getReversalOfId()).isEqualTo(originalJeId);
            assertThat(captured.getPostedDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(captured.getLines()).hasSize(1);
            // Verify DR/CR flip
            assertThat(captured.getLines().get(0).isCredit()).isTrue(); // original was false
            verify(outboxService).recordJournalPosted(eq(TENANT_ID), any(String.class), eq(reversal), isNull());
        }

        @Test
        @DisplayName("should skip entries that already have reversals (idempotency)")
        void shouldSkipExistingReversals() {
            when(accountingPeriodRepository.findById(PERIOD_ID))
                    .thenReturn(Optional.of(buildCurrentPeriod()));
            AccountingPeriod prior = buildPriorPeriod();
            when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(TENANT_ID))
                    .thenReturn(List.of(prior, buildCurrentPeriod()));

            UUID originalJeId = UUID.randomUUID();
            JournalEntry original = JournalEntry.builder().id(originalJeId).autoReverse(true).build();
            when(journalEntryRepository.findAutoReverseEntries(TENANT_ID, prior.getStartDate(), prior.getEndDate()))
                    .thenReturn(List.of(original));
            when(journalEntryRepository.findWithLinesByTenantIdAndId(TENANT_ID, originalJeId))
                    .thenReturn(Optional.of(original));
            when(journalEntryRepository.existsByTenantIdAndEventId(TENANT_ID, "AUTO-REVERSE:" + originalJeId))
                    .thenReturn(true); // Already reversed

            int count = service.generateReversals(TENANT_ID, PERIOD_ID, "system");

            assertThat(count).isZero();
            verify(ledgerPersistenceService, never()).persist(any());
        }

        @Test
        @DisplayName("should return 0 when no prior period found")
        void shouldReturnZeroWhenNoPriorPeriod() {
            when(accountingPeriodRepository.findById(PERIOD_ID))
                    .thenReturn(Optional.of(buildCurrentPeriod()));
            when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(TENANT_ID))
                    .thenReturn(List.of(buildCurrentPeriod())); // Only current, no prior

            int count = service.generateReversals(TENANT_ID, PERIOD_ID, "system");

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("should return 0 when no auto-reverse entries found")
        void shouldReturnZeroWhenNoAutoReverseEntries() {
            when(accountingPeriodRepository.findById(PERIOD_ID))
                    .thenReturn(Optional.of(buildCurrentPeriod()));
            AccountingPeriod prior = buildPriorPeriod();
            when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(TENANT_ID))
                    .thenReturn(List.of(prior, buildCurrentPeriod()));
            when(journalEntryRepository.findAutoReverseEntries(TENANT_ID, prior.getStartDate(), prior.getEndDate()))
                    .thenReturn(List.of());

            int count = service.generateReversals(TENANT_ID, PERIOD_ID, "system");

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("should throw when period not found")
        void shouldThrowWhenPeriodNotFound() {
            when(accountingPeriodRepository.findById(PERIOD_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateReversals(TENANT_ID, PERIOD_ID, "system"))
                    .isInstanceOf(AccountingPeriodNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when period belongs to different tenant")
        void shouldThrowWhenPeriodWrongTenant() {
            AccountingPeriod wrongTenant = AccountingPeriod.builder()
                    .periodId(PERIOD_ID)
                    .tenantId(UUID.randomUUID()) // Different tenant
                    .build();
            when(accountingPeriodRepository.findById(PERIOD_ID))
                    .thenReturn(Optional.of(wrongTenant));

            assertThatThrownBy(() -> service.generateReversals(TENANT_ID, PERIOD_ID, "system"))
                    .isInstanceOf(AccountingPeriodNotFoundException.class);
        }
    }
}
