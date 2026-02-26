package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateAccountingPeriodRequestDto;
import com.bracit.fisprocess.dto.response.AccountingPeriodResponseDto;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface AccountingPeriodService {
    AccountingPeriodResponseDto createPeriod(UUID tenantId, CreateAccountingPeriodRequestDto request);

    List<AccountingPeriodResponseDto> listPeriods(UUID tenantId, @Nullable PeriodStatus status);

    AccountingPeriodResponseDto changeStatus(UUID tenantId, UUID periodId, PeriodStatus targetStatus, String changedBy);
}
