package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.ActorRole;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.service.ActorRoleResolver;
import com.bracit.fisprocess.service.JournalEntryValidationService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.MultiCurrencyService;
import com.bracit.fisprocess.service.OutboxService;
import com.bracit.fisprocess.service.PeriodValidationService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalPostingEngine Unit Tests")
class JournalPostingEngineTest {

    @Mock private JournalEntryValidationService validationService;
    @Mock private LedgerPersistenceService ledgerPersistenceService;
    @Mock private PeriodValidationService periodValidationService;
    @Mock private MultiCurrencyService multiCurrencyService;
    @Mock private ActorRoleResolver actorRoleResolver;
    @Mock private OutboxService outboxService;

    private ModelMapper modelMapper;
    private JournalPostingEngine engine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDate POSTED_DATE = LocalDate.of(2026, 4, 13);

    @BeforeEach
    void setUp() {
        modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true);
        engine = new JournalPostingEngine(
                validationService, ledgerPersistenceService, periodValidationService,
                multiCurrencyService, actorRoleResolver, outboxService, modelMapper);
    }

    private DraftJournalEntry buildDraft() {
        return DraftJournalEntry.builder()
                .tenantId(TENANT_ID)
                .eventId("EVT-POST-001")
                .postedDate(POSTED_DATE)
                .effectiveDate(POSTED_DATE)
                .transactionDate(POSTED_DATE)
                .description("Test posting")
                .referenceId("REF-POST")
                .transactionCurrency("USD")
                .baseCurrency("USD")
                .exchangeRate(BigDecimal.ONE)
                .createdBy("tester")
                .lines(List.of(
                        DraftJournalLine.builder().accountCode("CASH").amountCents(1000L).baseAmountCents(1000L).isCredit(false).build(),
                        DraftJournalLine.builder().accountCode("REV").amountCents(1000L).baseAmountCents(1000L).isCredit(true).build()))
                .build();
    }

    private JournalEntry buildPersisted(DraftJournalEntry draft) {
        JournalEntry entry = JournalEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(draft.getTenantId())
                .eventId(draft.getEventId())
                .postedDate(draft.getPostedDate())
                .status(JournalStatus.POSTED)
                .transactionCurrency(draft.getTransactionCurrency())
                .baseCurrency(draft.getBaseCurrency())
                .exchangeRate(draft.getExchangeRate())
                .createdBy(draft.getCreatedBy())
                .build();
        for (DraftJournalLine dl : draft.getLines()) {
            Account acct = Account.builder()
                    .accountId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code(dl.getAccountCode())
                    .accountType(AccountType.ASSET)
                    .build();
            entry.addLine(JournalLine.builder()
                    .account(acct)
                    .amount(dl.getAmountCents())
                    .baseAmount(dl.getBaseAmountCents())
                    .isCredit(dl.isCredit())
                    .build());
        }
        return entry;
    }

    @Test
    @DisplayName("post should execute full pipeline: period validation → currency → validation → persist → outbox")
    void shouldExecuteFullPipeline() {
        DraftJournalEntry draft = buildDraft();
        DraftJournalEntry converted = buildDraft();
        when(actorRoleResolver.resolve(null)).thenReturn(ActorRole.FIS_ADMIN);
        when(multiCurrencyService.apply(draft)).thenReturn(converted);
        JournalEntry persisted = buildPersisted(converted);
        when(ledgerPersistenceService.persist(converted)).thenReturn(persisted);

        JournalEntryResponseDto result = engine.post(TENANT_ID, draft, null, null);

        verify(periodValidationService).validatePostingAllowed(eq(TENANT_ID), eq(POSTED_DATE), eq(ActorRole.FIS_ADMIN));
        verify(multiCurrencyService).apply(draft);
        verify(validationService).validate(converted);
        verify(ledgerPersistenceService).persist(converted);
        verify(outboxService).recordJournalPosted(eq(TENANT_ID), eq("EVT-POST-001"), eq(persisted), eq(null));

        assertThat(result).isNotNull();
        assertThat(result.getJournalEntryId()).isEqualTo(persisted.getId());
        assertThat(result.getLineCount()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(JournalStatus.POSTED);
    }

    @Test
    @DisplayName("post should propagate traceparent to outbox")
    void shouldPropagateTraceparent() {
        DraftJournalEntry draft = buildDraft();
        when(actorRoleResolver.resolve("FIS_ADMIN")).thenReturn(ActorRole.FIS_ADMIN);
        when(multiCurrencyService.apply(draft)).thenReturn(draft);
        JournalEntry persisted = buildPersisted(draft);
        when(ledgerPersistenceService.persist(draft)).thenReturn(persisted);

        engine.post(TENANT_ID, draft, "FIS_ADMIN", "00-abc123-def456-01");

        ArgumentCaptor<String> traceCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).recordJournalPosted(eq(TENANT_ID), eq("EVT-POST-001"), eq(persisted), traceCaptor.capture());
        assertThat(traceCaptor.getValue()).isEqualTo("00-abc123-def456-01");
    }

    @Test
    @DisplayName("post should set REVERSAL status when reversalOfId is present")
    void shouldPostReversal() {
        DraftJournalEntry draft = buildDraft();
        draft.setReversalOfId(UUID.randomUUID());
        when(actorRoleResolver.resolve(null)).thenReturn(ActorRole.FIS_ADMIN);
        when(multiCurrencyService.apply(draft)).thenReturn(draft);
        JournalEntry persisted = buildPersisted(draft);
        persisted.setReversalOfId(draft.getReversalOfId());
        persisted.setStatus(JournalStatus.REVERSAL);
        when(ledgerPersistenceService.persist(draft)).thenReturn(persisted);

        JournalEntryResponseDto result = engine.post(TENANT_ID, draft, null, null);

        assertThat(result.getStatus()).isEqualTo(JournalStatus.REVERSAL);
    }

    @Test
    @DisplayName("post should throw when period validation fails")
    void shouldThrowWhenPeriodValidationFails() {
        DraftJournalEntry draft = buildDraft();
        when(actorRoleResolver.resolve(null)).thenReturn(ActorRole.FIS_READER);
        RuntimeException periodEx = new RuntimeException("Period is hard closed");
        org.mockito.Mockito.doThrow(periodEx).when(periodValidationService)
                .validatePostingAllowed(any(), any(), any());

        assertThatThrownBy(() -> engine.post(TENANT_ID, draft, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Period is hard closed");

        verify(multiCurrencyService, org.mockito.Mockito.never()).apply(any());
    }

    @Test
    @DisplayName("post should throw when validation fails")
    void shouldThrowWhenValidationFails() {
        DraftJournalEntry draft = buildDraft();
        when(actorRoleResolver.resolve(null)).thenReturn(ActorRole.FIS_ADMIN);
        when(multiCurrencyService.apply(draft)).thenReturn(draft);
        RuntimeException validationEx = new RuntimeException("Unbalanced entry");
        org.mockito.Mockito.doThrow(validationEx).when(validationService).validate(draft);

        assertThatThrownBy(() -> engine.post(TENANT_ID, draft, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unbalanced entry");

        verify(ledgerPersistenceService, org.mockito.Mockito.never()).persist(any());
    }

    @Test
    @DisplayName("post should resolve actor role from header")
    void shouldResolveActorRole() {
        DraftJournalEntry draft = buildDraft();
        when(actorRoleResolver.resolve("FIS_ACCOUNTANT")).thenReturn(ActorRole.FIS_ACCOUNTANT);
        when(multiCurrencyService.apply(draft)).thenReturn(draft);
        when(ledgerPersistenceService.persist(draft)).thenReturn(buildPersisted(draft));

        engine.post(TENANT_ID, draft, "FIS_ACCOUNTANT", null);

        verify(periodValidationService).validatePostingAllowed(eq(TENANT_ID), eq(POSTED_DATE), eq(ActorRole.FIS_ACCOUNTANT));
    }
}
