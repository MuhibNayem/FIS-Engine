package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.enums.AuditAction;
import com.bracit.fisprocess.domain.enums.AuditEntityType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateAccountingPeriodRequestDto;
import com.bracit.fisprocess.dto.request.RunRevaluationRequestDto;
import com.bracit.fisprocess.dto.response.AccountingPeriodResponseDto;
import com.bracit.fisprocess.exception.InvalidPeriodTransitionException;
import com.bracit.fisprocess.exception.OverlappingAccountingPeriodException;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.service.AccountingPeriodService;
import com.bracit.fisprocess.service.AuditService;
import com.bracit.fisprocess.service.AutoReversalService;
import com.bracit.fisprocess.service.PeriodEndRevaluationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountingPeriodServiceImpl implements AccountingPeriodService {

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AuditService auditService;
    private final AutoReversalService autoReversalService;
    private final PeriodEndRevaluationService periodEndRevaluationService;

    @Value("${fis.revaluation.reserve-account-code:FX_REVAL_RESERVE}")
    private String defaultReserveAccountCode;
    @Value("${fis.revaluation.gain-account-code:FX_UNREALIZED_GAIN}")
    private String defaultGainAccountCode;
    @Value("${fis.revaluation.loss-account-code:FX_UNREALIZED_LOSS}")
    private String defaultLossAccountCode;

    @Override
    @Transactional
    public AccountingPeriodResponseDto createPeriod(UUID tenantId, CreateAccountingPeriodRequestDto request) {
        if (!accountingPeriodRepository.findOverlapping(tenantId, request.getStartDate(), request.getEndDate())
                .isEmpty()) {
            throw new OverlappingAccountingPeriodException();
        }
        AccountingPeriod created = accountingPeriodRepository.save(AccountingPeriod.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(PeriodStatus.OPEN)
                .build());
        auditService.logChange(
                tenantId,
                AuditEntityType.ACCOUNTING_PERIOD,
                created.getPeriodId(),
                AuditAction.CREATED,
                null,
                Map.of(
                        "name", created.getName(),
                        "startDate", created.getStartDate().toString(),
                        "endDate", created.getEndDate().toString(),
                        "status", created.getStatus().name()),
                "system");
        return toResponse(created);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDto> listPeriods(UUID tenantId, @Nullable PeriodStatus status) {
        return accountingPeriodRepository.listByTenantAndStatus(tenantId, status).stream().map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AccountingPeriodResponseDto changeStatus(UUID tenantId, UUID periodId, PeriodStatus targetStatus,
            String changedBy) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new InvalidPeriodTransitionException("Accounting period was not found for tenant."));

        PeriodStatus current = period.getStatus();
        Map<String, Object> oldValue = Map.of(
                "status", current.name(),
                "closedBy", String.valueOf(period.getClosedBy()),
                "closedAt", String.valueOf(period.getClosedAt()));
        if (current == targetStatus) {
            return toResponse(period);
        }

        if (current == PeriodStatus.OPEN && targetStatus == PeriodStatus.SOFT_CLOSED) {
            markClosed(period, changedBy);
        } else if (current == PeriodStatus.SOFT_CLOSED && targetStatus == PeriodStatus.OPEN) {
            reopen(period);
            autoReversalService.generateReversals(tenantId, periodId, changedBy);
        } else if (current == PeriodStatus.SOFT_CLOSED && targetStatus == PeriodStatus.HARD_CLOSED) {
            enforceSequentialHardClose(tenantId, period);
            periodEndRevaluationService.run(
                    tenantId,
                    period.getPeriodId(),
                    RunRevaluationRequestDto.builder()
                            .eventId("REVAL:" + period.getPeriodId())
                            .createdBy(changedBy)
                            .reserveAccountCode(defaultReserveAccountCode)
                            .gainAccountCode(defaultGainAccountCode)
                            .lossAccountCode(defaultLossAccountCode)
                            .build());
            markClosed(period, changedBy);
        } else if (current == PeriodStatus.HARD_CLOSED && targetStatus == PeriodStatus.OPEN) {
            enforceCascadingReopen(tenantId, period);
            reopen(period);
        } else {
            throw new InvalidPeriodTransitionException(
                    "Unsupported period transition: " + current + " -> " + targetStatus);
        }

        period.setStatus(targetStatus);
        AccountingPeriod saved = accountingPeriodRepository.save(period);
        auditService.logChange(
                tenantId,
                AuditEntityType.ACCOUNTING_PERIOD,
                saved.getPeriodId(),
                AuditAction.STATE_CHANGED,
                oldValue,
                Map.of(
                        "status", saved.getStatus().name(),
                        "closedBy", String.valueOf(saved.getClosedBy()),
                        "closedAt", String.valueOf(saved.getClosedAt())),
                changedBy);
        return toResponse(saved);
    }

    private void enforceSequentialHardClose(UUID tenantId, AccountingPeriod target) {
        List<AccountingPeriod> ordered = accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(tenantId);
        for (AccountingPeriod period : ordered) {
            if (period.getStartDate().isBefore(target.getStartDate())
                    && period.getStatus() != PeriodStatus.HARD_CLOSED) {
                throw new InvalidPeriodTransitionException("Prior periods must be HARD_CLOSED first.");
            }
        }
    }

    private void enforceCascadingReopen(UUID tenantId, AccountingPeriod target) {
        List<AccountingPeriod> later = accountingPeriodRepository
                .findByTenantIdAndStartDateAfterOrderByStartDateAsc(tenantId, target.getStartDate());
        for (AccountingPeriod period : later) {
            if (period.getStatus() != PeriodStatus.OPEN) {
                throw new InvalidPeriodTransitionException(
                        "All subsequent periods must be OPEN before reopening an earlier HARD_CLOSED period.");
            }
        }
    }

    private void markClosed(AccountingPeriod period, String changedBy) {
        period.setClosedBy(changedBy);
        period.setClosedAt(OffsetDateTime.now());
    }

    private void reopen(AccountingPeriod period) {
        period.setClosedBy(null);
        period.setClosedAt(null);
    }

    private AccountingPeriodResponseDto toResponse(AccountingPeriod period) {
        return AccountingPeriodResponseDto.builder()
                .periodId(period.getPeriodId())
                .name(period.getName())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .status(period.getStatus())
                .closedBy(period.getClosedBy())
                .closedAt(period.getClosedAt())
                .build();
    }
}
