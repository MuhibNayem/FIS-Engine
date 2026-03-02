package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.PeriodRevaluationRun;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.domain.enums.RevaluationRunStatus;
import com.bracit.fisprocess.dto.request.RunRevaluationRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.exception.RevaluationAlreadyRunException;
import com.bracit.fisprocess.exception.RevaluationConfigurationException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalExposureView;
import com.bracit.fisprocess.repository.JournalLineRepository;
import com.bracit.fisprocess.repository.PeriodRevaluationRunRepository;
import com.bracit.fisprocess.service.AuditService;
import com.bracit.fisprocess.service.ExchangeRateService;
import com.bracit.fisprocess.service.JournalEntryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PeriodEndRevaluationServiceImpl Unit Tests")
class PeriodEndRevaluationServiceImplTest {

    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;
    @Mock
    private PeriodRevaluationRunRepository runRepository;
    @Mock
    private JournalLineRepository journalLineRepository;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private JournalEntryService journalEntryService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PeriodEndRevaluationServiceImpl service;

    @Test
    @DisplayName("run should return existing result when already completed")
    void runShouldReturnExistingCompletedRun() {
        UUID tenantId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID jeId = UUID.randomUUID();

        when(runRepository.findByTenantIdAndPeriodId(tenantId, periodId)).thenReturn(Optional.of(
                PeriodRevaluationRun.builder()
                        .id(runId)
                        .tenantId(tenantId)
                        .periodId(periodId)
                        .status(RevaluationRunStatus.COMPLETED)
                        .details(Map.of("generatedJournalEntryIds", List.of(jeId.toString())))
                        .build()));

        var response = service.run(tenantId, periodId, request("EVT1"));

        assertThat(response.getRunId()).isEqualTo(runId);
        assertThat(response.getGeneratedJournalEntryIds()).containsExactly(jeId);
    }

    @Test
    @DisplayName("run should fail when processing run already exists")
    void runShouldFailWhenProcessingRunExists() {
        UUID tenantId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        when(runRepository.findByTenantIdAndPeriodId(tenantId, periodId)).thenReturn(Optional.of(
                PeriodRevaluationRun.builder()
                        .id(UUID.randomUUID())
                        .status(RevaluationRunStatus.PROCESSING)
                        .build()));

        assertThatThrownBy(() -> service.run(tenantId, periodId, request("EVT2")))
                .isInstanceOf(RevaluationAlreadyRunException.class);
    }

    @Test
    @DisplayName("run should fail for non-closed period")
    void runShouldFailForOpenPeriod() {
        UUID tenantId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        when(runRepository.findByTenantIdAndPeriodId(tenantId, periodId)).thenReturn(Optional.empty());
        when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(AccountingPeriod.builder()
                .periodId(periodId)
                .tenantId(tenantId)
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .status(PeriodStatus.OPEN)
                .build()));

        assertThatThrownBy(() -> service.run(tenantId, periodId, request("EVT3")))
                .isInstanceOf(RevaluationConfigurationException.class);
    }

    @Test
    @DisplayName("run should post revaluation journal entry when currency delta exists")
    void runShouldPostJournalEntryWhenExposureDeltaExists() {
        UUID tenantId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID postedId = UUID.randomUUID();
        AccountingPeriod period = AccountingPeriod.builder()
                .periodId(periodId)
                .tenantId(tenantId)
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .status(PeriodStatus.SOFT_CLOSED)
                .build();

        JournalExposureView exposure = mock(JournalExposureView.class);
        when(exposure.getTransactionCurrency()).thenReturn("EUR");
        when(exposure.getSignedAmountCents()).thenReturn(1_000L);
        when(exposure.getSignedBaseAmountCents()).thenReturn(950L);

        when(runRepository.findByTenantIdAndPeriodId(tenantId, periodId)).thenReturn(Optional.empty());
        when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(runRepository.save(any(PeriodRevaluationRun.class))).thenAnswer(inv -> {
            PeriodRevaluationRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_REVAL_RESERVE")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("FX_REVAL_RESERVE")
                .currencyCode("USD")
                .build()));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_UNREALIZED_GAIN")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("FX_UNREALIZED_GAIN")
                .currencyCode("USD")
                .build()));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_UNREALIZED_LOSS")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("FX_UNREALIZED_LOSS")
                .currencyCode("USD")
                .build()));
        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder()
                        .tenantId(tenantId)
                        .name("Tenant")
                        .baseCurrency("USD")
                        .isActive(true)
                        .build()));
        when(journalLineRepository.aggregateExposureByCurrency(tenantId, period.getStartDate(), period.getEndDate()))
                .thenReturn(List.of(exposure));
        when(exchangeRateService.resolveRate(eq(tenantId), eq("EUR"), eq("USD"), eq(period.getEndDate())))
                .thenReturn(new java.math.BigDecimal("1.10"));
        when(journalEntryService.createJournalEntry(eq(tenantId), any(CreateJournalEntryRequestDto.class), eq("FIS_ADMIN")))
                .thenReturn(JournalEntryResponseDto.builder().journalEntryId(postedId).build());

        var response = service.run(tenantId, periodId, request("EVT5"));

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getGeneratedJournalEntryIds()).containsExactly(postedId);
        verify(journalEntryService).createJournalEntry(eq(tenantId), any(CreateJournalEntryRequestDto.class), eq("FIS_ADMIN"));
    }

    @Test
    @DisplayName("run should complete with empty generation when no exposure exists")
    void runShouldCompleteWhenNoExposureExists() {
        UUID tenantId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        AccountingPeriod period = AccountingPeriod.builder()
                .periodId(periodId)
                .tenantId(tenantId)
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .status(PeriodStatus.SOFT_CLOSED)
                .build();
        when(runRepository.findByTenantIdAndPeriodId(tenantId, periodId)).thenReturn(Optional.empty());
        when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(runRepository.save(any(PeriodRevaluationRun.class))).thenAnswer(inv -> {
            PeriodRevaluationRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_REVAL_RESERVE")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("FX_REVAL_RESERVE")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .build()));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_UNREALIZED_GAIN")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("FX_UNREALIZED_GAIN")
                .accountType(AccountType.REVENUE)
                .currencyCode("USD")
                .build()));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_UNREALIZED_LOSS")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("FX_UNREALIZED_LOSS")
                .accountType(AccountType.EXPENSE)
                .currencyCode("USD")
                .build()));
        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder()
                        .tenantId(tenantId)
                        .name("Tenant")
                        .baseCurrency("USD")
                        .isActive(true)
                        .build()));
        when(journalLineRepository.aggregateExposureByCurrency(tenantId, period.getStartDate(), period.getEndDate()))
                .thenReturn(List.of());

        var response = service.run(tenantId, periodId, request("EVT4"));

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getGeneratedJournalEntryIds()).isEmpty();
        verify(auditService).logChange(any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<PeriodRevaluationRun> runCaptor = ArgumentCaptor.forClass(PeriodRevaluationRun.class);
        verify(runRepository, atLeastOnce()).save(runCaptor.capture());
        PeriodRevaluationRun lastSaved = runCaptor.getAllValues().getLast();
        assertThat(lastSaved.getDetails()).containsKey("currencySnapshots");
        assertThat(lastSaved.getDetails().get("currencySnapshots")).isInstanceOf(List.class);
        assertThat((List<?>) lastSaved.getDetails().get("currencySnapshots")).isEmpty();
    }

    @Test
    @DisplayName("run should return empty generated ids when existing details payload is malformed")
    void runShouldReturnEmptyGeneratedIdsWhenExistingDetailsMalformed() {
        UUID tenantId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        when(runRepository.findByTenantIdAndPeriodId(tenantId, periodId)).thenReturn(Optional.of(
                PeriodRevaluationRun.builder()
                        .id(runId)
                        .tenantId(tenantId)
                        .periodId(periodId)
                        .status(RevaluationRunStatus.COMPLETED)
                        .details(Map.of("generatedJournalEntryIds", "not-a-list"))
                        .build()));

        var response = service.run(tenantId, periodId, request("EVT6"));

        assertThat(response.getRunId()).isEqualTo(runId);
        assertThat(response.getGeneratedJournalEntryIds()).isEmpty();
    }

    private RunRevaluationRequestDto request(String eventId) {
        return RunRevaluationRequestDto.builder()
                .eventId(eventId)
                .createdBy("admin")
                .reserveAccountCode("FX_REVAL_RESERVE")
                .gainAccountCode("FX_UNREALIZED_GAIN")
                .lossAccountCode("FX_UNREALIZED_LOSS")
                .build();
    }
}
