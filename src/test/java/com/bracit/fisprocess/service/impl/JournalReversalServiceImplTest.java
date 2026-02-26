package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.dto.request.CorrectionRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.ReversalRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.exception.InvalidReversalException;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.IdempotentLedgerWriteService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.OutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalReversalServiceImpl Unit Tests")
class JournalReversalServiceImplTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private LedgerPersistenceService ledgerPersistenceService;
    @Mock
    private OutboxService outboxService;
    @Mock
    private JournalEntryService journalEntryService;
    @Mock
    private IdempotentLedgerWriteService idempotentLedgerWriteService;

    @InjectMocks
    private JournalReversalServiceImpl service;

    @Test
    @DisplayName("performReverse should create mirrored credit/debit lines")
    void performReverseShouldMirrorLines() {
        UUID tenantId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        JournalEntry original = journalWithSingleDebit(originalId, tenantId);
        when(journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, originalId))
                .thenReturn(Optional.of(original));
        when(journalEntryRepository.existsByReversalOfId(originalId)).thenReturn(false);
        when(ledgerPersistenceService.persist(any())).thenReturn(JournalEntry.builder()
                .id(UUID.randomUUID())
                .status(JournalStatus.REVERSAL)
                .build());

        ReversalRequestDto request = ReversalRequestDto.builder()
                .eventId("REV-1")
                .reason("correction")
                .createdBy("alice")
                .build();

        var response = service.performReverse(tenantId, originalId, request);

        ArgumentCaptor<com.bracit.fisprocess.domain.model.DraftJournalEntry> captor = ArgumentCaptor.forClass(
                com.bracit.fisprocess.domain.model.DraftJournalEntry.class);
        verify(ledgerPersistenceService).persist(captor.capture());
        var draft = captor.getValue();

        assertThat(draft.getReversalOfId()).isEqualTo(originalId);
        assertThat(draft.getLines()).hasSize(1);
        assertThat(draft.getLines().get(0).isCredit()).isTrue();
        assertThat(response.getOriginalJournalEntryId()).isEqualTo(originalId);
    }

    @Test
    @DisplayName("performReverse should reject double reversal")
    void performReverseShouldRejectDoubleReversal() {
        UUID tenantId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        when(journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, originalId))
                .thenReturn(Optional.of(journalWithSingleDebit(originalId, tenantId)));
        when(journalEntryRepository.existsByReversalOfId(originalId)).thenReturn(true);

        assertThatThrownBy(() -> service.performReverse(tenantId, originalId,
                ReversalRequestDto.builder().eventId("REV-2").reason("dup").createdBy("alice").build()))
                .isInstanceOf(InvalidReversalException.class);
    }

    @Test
    @DisplayName("performCorrection should reverse and post replacement")
    void performCorrectionShouldReverseAndReplace() {
        UUID tenantId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        UUID reversalId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();

        when(journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, originalId))
                .thenReturn(Optional.of(journalWithSingleDebit(originalId, tenantId)));
        when(journalEntryRepository.existsByReversalOfId(originalId)).thenReturn(false);
        when(ledgerPersistenceService.persist(any())).thenReturn(JournalEntry.builder()
                .id(reversalId)
                .status(JournalStatus.REVERSAL)
                .build());
        when(journalEntryService.createJournalEntry(any(), any())).thenReturn(
                JournalEntryResponseDto.builder()
                        .journalEntryId(replacementId)
                        .status(JournalStatus.POSTED)
                        .build());

        CorrectionRequestDto request = CorrectionRequestDto.builder()
                .eventId("CORR-REPL")
                .reversalEventId("CORR-REV")
                .postedDate(LocalDate.of(2026, 2, 2))
                .description("fix")
                .referenceId("REF")
                .transactionCurrency("USD")
                .createdBy("alice")
                .lines(List.of(JournalLineRequestDto.builder()
                        .accountCode("CASH")
                        .amountCents(100L)
                        .isCredit(false)
                        .build()))
                .build();

        var response = service.performCorrection(tenantId, originalId, request);

        assertThat(response.getReversalJournalEntryId()).isEqualTo(reversalId);
        assertThat(response.getStatus()).isEqualTo("POSTED");
        verify(journalEntryService).createJournalEntry(any(), any());
    }

    private JournalEntry journalWithSingleDebit(UUID id, UUID tenantId) {
        Account account = Account.builder()
                .accountId(UUID.randomUUID())
                .tenantId(tenantId)
                .code("CASH")
                .name("Cash")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .build();
        JournalLine line = JournalLine.builder()
                .id(UUID.randomUUID())
                .account(account)
                .amount(100L)
                .baseAmount(100L)
                .isCredit(false)
                .build();
        JournalEntry entry = JournalEntry.builder()
                .id(id)
                .tenantId(tenantId)
                .eventId("E1")
                .postedDate(LocalDate.of(2026, 2, 1))
                .status(JournalStatus.POSTED)
                .transactionCurrency("USD")
                .baseCurrency("USD")
                .exchangeRate(BigDecimal.ONE)
                .createdBy("system")
                .previousHash("0")
                .hash("h1")
                .build();
        entry.addLine(line);
        return entry;
    }
}
