package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.PeriodRevaluationRun;
import com.bracit.fisprocess.domain.enums.AuditAction;
import com.bracit.fisprocess.domain.enums.AuditEntityType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.domain.enums.RevaluationRunStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.RunRevaluationRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.dto.response.RevaluationResponseDto;
import com.bracit.fisprocess.exception.AccountingPeriodNotFoundException;
import com.bracit.fisprocess.exception.RevaluationAlreadyRunException;
import com.bracit.fisprocess.exception.RevaluationConfigurationException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.JournalExposureView;
import com.bracit.fisprocess.repository.JournalLineRepository;
import com.bracit.fisprocess.repository.PeriodRevaluationRunRepository;
import com.bracit.fisprocess.service.AuditService;
import com.bracit.fisprocess.service.ExchangeRateService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PeriodEndRevaluationService;
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
public class PeriodEndRevaluationServiceImpl implements PeriodEndRevaluationService {

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final PeriodRevaluationRunRepository runRepository;
    private final JournalLineRepository journalLineRepository;
    private final ExchangeRateService exchangeRateService;
    private final JournalEntryService journalEntryService;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public RevaluationResponseDto run(UUID tenantId, UUID periodId, RunRevaluationRequestDto request) {
        Optional<PeriodRevaluationRun> existing = runRepository.findByTenantIdAndPeriodId(tenantId, periodId);
        if (existing.isPresent()) {
            PeriodRevaluationRun run = existing.get();
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
                    "Period must be SOFT_CLOSED or HARD_CLOSED before revaluation can run.");
        }

        PeriodRevaluationRun run = runRepository.save(PeriodRevaluationRun.builder()
                .tenantId(tenantId)
                .periodId(periodId)
                .eventId(request.getEventId())
                .status(RevaluationRunStatus.PROCESSING)
                .createdBy(request.getCreatedBy())
                .build());

        try {
            RevaluationExecutionResult execution = executeRevaluation(tenantId, period, request);

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
                    AuditEntityType.REVALUATION,
                    run.getId(),
                    AuditAction.CREATED,
                    null,
                    details,
                    request.getCreatedBy());

            return RevaluationResponseDto.builder()
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

    private RevaluationExecutionResult executeRevaluation(UUID tenantId, AccountingPeriod period, RunRevaluationRequestDto request) {
        List<JournalExposureView> exposure = journalLineRepository.aggregateExposureByCurrency(
                tenantId, period.getStartDate(), period.getEndDate());
        List<UUID> generated = new ArrayList<>();
        List<Map<String, Object>> snapshots = new ArrayList<>();

        String reserveCode = request.getReserveAccountCode();
        String gainCode = request.getGainAccountCode();
        String lossCode = request.getLossAccountCode();
        validateAccountExists(tenantId, reserveCode);
        validateAccountExists(tenantId, gainCode);
        validateAccountExists(tenantId, lossCode);
        String baseCurrency = resolveBaseCurrency(tenantId, reserveCode);

        for (JournalExposureView row : exposure) {
            String txCurrency = row.getTransactionCurrency();
            long netAmountCents = row.getSignedAmountCents();
            long carryingBaseCents = row.getSignedBaseAmountCents();
            if (netAmountCents == 0L) {
                snapshots.add(snapshot(txCurrency, null, 0L, carryingBaseCents, 0L, 0L, null));
                continue;
            }

            BigDecimal closingRate = exchangeRateService.resolveRate(
                    tenantId, txCurrency, baseCurrency, period.getEndDate());
            long closingBaseCents = BigDecimal.valueOf(netAmountCents)
                    .multiply(closingRate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            long deltaCents = closingBaseCents - carryingBaseCents;
            if (deltaCents == 0L) {
                snapshots.add(snapshot(txCurrency, closingRate, netAmountCents, carryingBaseCents, closingBaseCents, 0L, null));
                continue;
            }

            boolean gain = deltaCents > 0;
            long amount = Math.abs(deltaCents);
            String counterCode = gain ? gainCode : lossCode;

            JournalEntryResponseDto posted = journalEntryService.createJournalEntry(
                    tenantId,
                    CreateJournalEntryRequestDto.builder()
                            .eventId(request.getEventId() + ":" + txCurrency)
                            .postedDate(period.getEndDate())
                            .description("Period-end revaluation for " + txCurrency + " during " + period.getName())
                            .referenceId("REVAL-" + period.getName() + "-" + txCurrency)
                            .transactionCurrency(baseCurrency)
                            .createdBy(request.getCreatedBy())
                            .lines(List.of(
                                    JournalLineRequestDto.builder()
                                            .accountCode(gain ? reserveCode : counterCode)
                                            .amountCents(amount)
                                            .isCredit(false)
                                            .build(),
                                    JournalLineRequestDto.builder()
                                            .accountCode(gain ? counterCode : reserveCode)
                                            .amountCents(amount)
                                            .isCredit(true)
                                            .build()))
                            .build(),
                    "FIS_ADMIN");
            generated.add(posted.getJournalEntryId());
            snapshots.add(snapshot(
                    txCurrency,
                    closingRate,
                    netAmountCents,
                    carryingBaseCents,
                    closingBaseCents,
                    deltaCents,
                    posted.getJournalEntryId()));
        }

        return new RevaluationExecutionResult(generated, snapshots);
    }

    private String resolveBaseCurrency(UUID tenantId, String anyAccountCode) {
        Account account = accountRepository.findByTenantIdAndCode(tenantId, anyAccountCode)
                .orElseThrow(() -> new RevaluationConfigurationException(
                        "Revaluation account '" + anyAccountCode + "' not found."));
        return account.getCurrencyCode();
    }

    private void validateAccountExists(UUID tenantId, String accountCode) {
        accountRepository.findByTenantIdAndCode(tenantId, accountCode)
                .orElseThrow(() -> new RevaluationConfigurationException(
                        "Revaluation account '" + accountCode + "' not found."));
    }

    private RevaluationResponseDto fromExisting(UUID periodId, PeriodRevaluationRun run) {
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
        return RevaluationResponseDto.builder()
                .periodId(periodId)
                .runId(run.getId())
                .status(run.getStatus().name())
                .generatedJournalEntryIds(ids)
                .build();
    }

    private static Map<String, Object> snapshot(String currency, BigDecimal closingRate, long signedAmountCents,
            long carryingBaseCents, long translatedBaseCents, long deltaCents, UUID postedJournalEntryId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("currency", currency);
        snapshot.put("rateType", "CLOSING");
        snapshot.put("rateUsed", closingRate);
        snapshot.put("signedAmountCents", signedAmountCents);
        snapshot.put("carryingBaseCents", carryingBaseCents);
        snapshot.put("translatedBaseCents", translatedBaseCents);
        snapshot.put("deltaCents", deltaCents);
        snapshot.put("postedJournalEntryId", postedJournalEntryId);
        return snapshot;
    }

    private record RevaluationExecutionResult(
            List<UUID> generatedJournalEntryIds,
            List<Map<String, Object>> currencySnapshots) {
    }
}
