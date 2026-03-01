package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.PeriodTranslationRun;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.AuditAction;
import com.bracit.fisprocess.domain.enums.AuditEntityType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.domain.enums.RevaluationRunStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.RunTranslationRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.dto.response.TranslationResponseDto;
import com.bracit.fisprocess.exception.AccountingPeriodNotFoundException;
import com.bracit.fisprocess.exception.RevaluationAlreadyRunException;
import com.bracit.fisprocess.exception.RevaluationConfigurationException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.IncomeStatementExposureView;
import com.bracit.fisprocess.repository.JournalLineRepository;
import com.bracit.fisprocess.repository.PeriodTranslationRunRepository;
import com.bracit.fisprocess.service.AuditService;
import com.bracit.fisprocess.service.ExchangeRateService;
import com.bracit.fisprocess.service.FunctionalCurrencyTranslationService;
import com.bracit.fisprocess.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FunctionalCurrencyTranslationServiceImpl implements FunctionalCurrencyTranslationService {

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final PeriodTranslationRunRepository runRepository;
    private final JournalLineRepository journalLineRepository;
    private final ExchangeRateService exchangeRateService;
    private final JournalEntryService journalEntryService;
    private final AccountRepository accountRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public TranslationResponseDto run(UUID tenantId, UUID periodId, RunTranslationRequestDto request) {
        Optional<PeriodTranslationRun> existing = runRepository.findByTenantIdAndPeriodId(tenantId, periodId);
        if (existing.isPresent()) {
            PeriodTranslationRun run = existing.get();
            if (run.getStatus() == RevaluationRunStatus.COMPLETED) {
                return fromExisting(periodId, run);
            }
            throw new RevaluationAlreadyRunException(periodId);
        }

        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(AccountingPeriodNotFoundException::new);
        if (period.getStatus() != PeriodStatus.SOFT_CLOSED && period.getStatus() != PeriodStatus.HARD_CLOSED) {
            throw new RevaluationConfigurationException(
                    "Period must be SOFT_CLOSED or HARD_CLOSED before functional translation can run.");
        }

        PeriodTranslationRun run = runRepository.save(PeriodTranslationRun.builder()
                .tenantId(tenantId)
                .periodId(periodId)
                .eventId(request.getEventId())
                .status(RevaluationRunStatus.PROCESSING)
                .createdBy(request.getCreatedBy())
                .build());

        try {
            TranslationExecutionResult execution = executeTranslation(tenantId, period, request);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("generatedJournalEntryIds", execution.generatedJournalEntryIds());
            details.put("generatedCount", execution.generatedJournalEntryIds().size());
            details.put("currencySnapshots", execution.currencySnapshots());

            run.setStatus(RevaluationRunStatus.COMPLETED);
            run.setCompletedAt(OffsetDateTime.now());
            run.setDetails(details);
            runRepository.save(run);

            auditService.logChange(
                    tenantId,
                    AuditEntityType.TRANSLATION,
                    run.getId(),
                    AuditAction.CREATED,
                    null,
                    details,
                    request.getCreatedBy());

            return TranslationResponseDto.builder()
                    .periodId(periodId)
                    .runId(run.getId())
                    .status(run.getStatus().name())
                    .generatedJournalEntryIds(execution.generatedJournalEntryIds())
                    .build();
        } catch (RuntimeException ex) {
            run.setStatus(RevaluationRunStatus.FAILED);
            run.setCompletedAt(OffsetDateTime.now());
            run.setDetails(Map.of("error", ex.getMessage()));
            runRepository.save(run);
            throw ex;
        }
    }

    private TranslationExecutionResult executeTranslation(UUID tenantId, AccountingPeriod period, RunTranslationRequestDto request) {
        validateTranslationAccounts(tenantId, request.getCtaOciAccountCode(), request.getTranslationReserveAccountCode());
        String baseCurrency = businessEntityRepository.findById(tenantId)
                .map(BusinessEntity::getBaseCurrency)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        List<IncomeStatementExposureView> exposures = journalLineRepository.aggregateIncomeStatementExposureByCurrency(
                tenantId, period.getStartDate(), period.getEndDate());
        List<UUID> generated = new ArrayList<>();
        List<Map<String, Object>> snapshots = new ArrayList<>();

        for (IncomeStatementExposureView row : exposures) {
            String txCurrency = row.getTransactionCurrency();
            long signedTxAmountCents = row.getSignedAmountCents();
            long carryingBaseCents = row.getSignedBaseAmountCents();
            if (signedTxAmountCents == 0L) {
                snapshots.add(snapshot(txCurrency, null, 0L, carryingBaseCents, 0L, 0L, null));
                continue;
            }

            BigDecimal averageRate = exchangeRateService.resolveAverageRate(
                    tenantId, txCurrency, baseCurrency, period.getStartDate(), period.getEndDate());
            long translatedBaseCents = BigDecimal.valueOf(signedTxAmountCents)
                    .multiply(averageRate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            long deltaCents = translatedBaseCents - carryingBaseCents;
            if (deltaCents == 0L) {
                snapshots.add(snapshot(txCurrency, averageRate, signedTxAmountCents, carryingBaseCents, translatedBaseCents, 0L, null));
                continue;
            }

            boolean deltaIncrease = deltaCents > 0;
            long amount = Math.abs(deltaCents);

            JournalLineRequestDto debitLine = JournalLineRequestDto.builder()
                    .accountCode(deltaIncrease ? request.getTranslationReserveAccountCode() : request.getCtaOciAccountCode())
                    .amountCents(amount)
                    .isCredit(false)
                    .build();
            JournalLineRequestDto creditLine = JournalLineRequestDto.builder()
                    .accountCode(deltaIncrease ? request.getCtaOciAccountCode() : request.getTranslationReserveAccountCode())
                    .amountCents(amount)
                    .isCredit(true)
                    .build();

            JournalEntryResponseDto posted = journalEntryService.createJournalEntry(
                    tenantId,
                    CreateJournalEntryRequestDto.builder()
                            .eventId(request.getEventId() + ":" + txCurrency)
                            .postedDate(period.getEndDate())
                            .effectiveDate(period.getEndDate())
                            .transactionDate(period.getEndDate())
                            .description("Functional currency translation CTA for " + txCurrency + " in " + period.getName())
                            .referenceId("TRANSLATION-" + period.getName() + "-" + txCurrency)
                            .transactionCurrency(baseCurrency)
                            .createdBy(request.getCreatedBy())
                            .lines(List.of(debitLine, creditLine))
                            .build(),
                    "FIS_ADMIN");
            generated.add(posted.getJournalEntryId());
            snapshots.add(snapshot(
                    txCurrency,
                    averageRate,
                    signedTxAmountCents,
                    carryingBaseCents,
                    translatedBaseCents,
                    deltaCents,
                    posted.getJournalEntryId()));
        }

        return new TranslationExecutionResult(generated, snapshots);
    }

    private void validateTranslationAccounts(UUID tenantId, String ctaOciAccountCode, String reserveAccountCode) {
        Account cta = accountRepository.findByTenantIdAndCode(tenantId, ctaOciAccountCode)
                .orElseThrow(() -> new RevaluationConfigurationException(
                        "Translation account '" + ctaOciAccountCode + "' not found."));
        Account reserve = accountRepository.findByTenantIdAndCode(tenantId, reserveAccountCode)
                .orElseThrow(() -> new RevaluationConfigurationException(
                        "Translation account '" + reserveAccountCode + "' not found."));

        if (cta.getAccountType() != AccountType.EQUITY) {
            throw new RevaluationConfigurationException("CTA OCI account must be EQUITY type.");
        }
        if (reserve.getAccountType() != AccountType.EQUITY) {
            throw new RevaluationConfigurationException("Translation reserve account must be EQUITY type.");
        }
    }

    private TranslationResponseDto fromExisting(UUID periodId, PeriodTranslationRun run) {
        List<UUID> ids;
        if (run.getDetails() == null) {
            ids = List.of();
        } else {
            Object raw = run.getDetails().getOrDefault("generatedJournalEntryIds", List.of());
            if (raw instanceof List<?> list) {
                ids = list.stream().map(value -> UUID.fromString(String.valueOf(value))).toList();
            } else {
                ids = List.of();
            }
        }
        return TranslationResponseDto.builder()
                .periodId(periodId)
                .runId(run.getId())
                .status(run.getStatus().name())
                .generatedJournalEntryIds(ids)
                .build();
    }

    private static Map<String, Object> snapshot(String currency, BigDecimal averageRate, long signedAmountCents,
            long carryingBaseCents, long translatedBaseCents, long deltaCents, UUID postedJournalEntryId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("currency", currency);
        snapshot.put("rateType", "AVERAGE");
        snapshot.put("rateUsed", averageRate);
        snapshot.put("signedAmountCents", signedAmountCents);
        snapshot.put("carryingBaseCents", carryingBaseCents);
        snapshot.put("translatedBaseCents", translatedBaseCents);
        snapshot.put("deltaCents", deltaCents);
        snapshot.put("postedJournalEntryId", postedJournalEntryId);
        return snapshot;
    }

    private record TranslationExecutionResult(
            List<UUID> generatedJournalEntryIds,
            List<Map<String, Object>> currencySnapshots) {
    }
}
