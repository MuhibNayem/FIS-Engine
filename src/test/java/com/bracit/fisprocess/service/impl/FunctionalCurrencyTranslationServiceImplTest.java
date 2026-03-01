package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.PeriodTranslationRun;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.domain.enums.RevaluationRunStatus;
import com.bracit.fisprocess.dto.request.RunTranslationRequestDto;
import com.bracit.fisprocess.exception.RevaluationConfigurationException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalLineRepository;
import com.bracit.fisprocess.repository.PeriodTranslationRunRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FunctionalCurrencyTranslationServiceImpl Unit Tests")
class FunctionalCurrencyTranslationServiceImplTest {

    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;
    @Mock
    private PeriodTranslationRunRepository runRepository;
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
    private FunctionalCurrencyTranslationServiceImpl service;

    @Test
    @DisplayName("run should fail when CTA account is not EQUITY")
    void runShouldFailForNonEquityCtaAccount() {
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
        when(runRepository.save(any(PeriodTranslationRun.class))).thenAnswer(inv -> {
            PeriodTranslationRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
        when(accountRepository.findByTenantIdAndCode(tenantId, "CTA")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("CTA")
                .accountType(AccountType.REVENUE)
                .currencyCode("USD")
                .build()));
        when(accountRepository.findByTenantIdAndCode(tenantId, "RESERVE")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("RESERVE")
                .accountType(AccountType.EQUITY)
                .currencyCode("USD")
                .build()));

        assertThatThrownBy(() -> service.run(tenantId, periodId, request("EVT-T1")))
                .isInstanceOf(RevaluationConfigurationException.class)
                .hasMessageContaining("CTA OCI account must be EQUITY");
    }

    @Test
    @DisplayName("run should complete with empty output when no exposure exists")
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
        when(runRepository.save(any(PeriodTranslationRun.class))).thenAnswer(inv -> {
            PeriodTranslationRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
        when(accountRepository.findByTenantIdAndCode(tenantId, "CTA")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("CTA")
                .accountType(AccountType.EQUITY)
                .currencyCode("USD")
                .build()));
        when(accountRepository.findByTenantIdAndCode(tenantId, "RESERVE")).thenReturn(Optional.of(Account.builder()
                .tenantId(tenantId)
                .code("RESERVE")
                .accountType(AccountType.EQUITY)
                .currencyCode("USD")
                .build()));
        when(businessEntityRepository.findById(tenantId)).thenReturn(Optional.of(BusinessEntity.builder()
                .tenantId(tenantId)
                .name("Tenant")
                .baseCurrency("USD")
                .isActive(true)
                .build()));
        when(journalLineRepository.aggregateIncomeStatementExposureByCurrency(
                tenantId, period.getStartDate(), period.getEndDate())).thenReturn(List.of());

        var response = service.run(tenantId, periodId, request("EVT-T2"));

        assertThat(response.getStatus()).isEqualTo(RevaluationRunStatus.COMPLETED.name());
        assertThat(response.getGeneratedJournalEntryIds()).isEmpty();
        verify(auditService).logChange(any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<PeriodTranslationRun> runCaptor = ArgumentCaptor.forClass(PeriodTranslationRun.class);
        verify(runRepository, atLeastOnce()).save(runCaptor.capture());
        PeriodTranslationRun lastSaved = runCaptor.getAllValues().getLast();
        assertThat(lastSaved.getDetails()).containsKey("currencySnapshots");
        assertThat(lastSaved.getDetails().get("currencySnapshots")).isInstanceOf(List.class);
        assertThat((List<?>) lastSaved.getDetails().get("currencySnapshots")).isEmpty();
    }

    private RunTranslationRequestDto request(String eventId) {
        return RunTranslationRequestDto.builder()
                .eventId(eventId)
                .createdBy("admin")
                .ctaOciAccountCode("CTA")
                .translationReserveAccountCode("RESERVE")
                .build();
    }
}
