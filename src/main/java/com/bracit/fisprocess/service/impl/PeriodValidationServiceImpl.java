package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.enums.ActorRole;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.exception.AccountingPeriodNotFoundException;
import com.bracit.fisprocess.exception.PeriodClosedException;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.service.PeriodValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PeriodValidationServiceImpl implements PeriodValidationService {

    private final AccountingPeriodRepository accountingPeriodRepository;

    @Override
    public void validatePostingAllowed(UUID tenantId, LocalDate postedDate, ActorRole actorRole) {
        AccountingPeriod period = accountingPeriodRepository.findContainingDate(tenantId, postedDate)
                .orElseThrow(AccountingPeriodNotFoundException::new);
        if (period.getStatus() == PeriodStatus.HARD_CLOSED) {
            throw new PeriodClosedException("Posting blocked: accounting period is HARD_CLOSED.");
        }
        if (period.getStatus() == PeriodStatus.SOFT_CLOSED && actorRole != ActorRole.FIS_ADMIN) {
            throw new PeriodClosedException("Posting blocked: accounting period is SOFT_CLOSED.");
        }
    }
}
