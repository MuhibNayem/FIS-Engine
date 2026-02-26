package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateAccountingPeriodRequestDto;
import com.bracit.fisprocess.exception.InvalidPeriodTransitionException;
import com.bracit.fisprocess.exception.OverlappingAccountingPeriodException;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.service.AuditService;
import com.bracit.fisprocess.service.PeriodEndRevaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
@DisplayName("AccountingPeriodServiceImpl Unit Tests")
class AccountingPeriodServiceImplTest {

    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private PeriodEndRevaluationService periodEndRevaluationService;

    @InjectMocks
    private AccountingPeriodServiceImpl service;

    @Test
    @DisplayName("createPeriod should reject overlapping periods")
    void createPeriodShouldRejectOverlap() {
        UUID tenantId = UUID.randomUUID();
        CreateAccountingPeriodRequestDto request = CreateAccountingPeriodRequestDto.builder()
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .build();
        when(accountingPeriodRepository.findOverlapping(tenantId, request.getStartDate(), request.getEndDate()))
                .thenReturn(List.of(AccountingPeriod.builder().build()));

        assertThatThrownBy(() -> service.createPeriod(tenantId, request))
                .isInstanceOf(OverlappingAccountingPeriodException.class);
    }

    @Test
    @DisplayName("changeStatus should enforce sequential HARD_CLOSE")
    void changeStatusShouldEnforceSequentialHardClose() {
        UUID tenantId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        AccountingPeriod target = AccountingPeriod.builder()
                .periodId(targetId)
                .tenantId(tenantId)
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .status(PeriodStatus.SOFT_CLOSED)
                .build();
        when(accountingPeriodRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(tenantId)).thenReturn(List.of(
                AccountingPeriod.builder()
                        .tenantId(tenantId)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .status(PeriodStatus.SOFT_CLOSED)
                        .build(),
                target));

        assertThatThrownBy(() -> service.changeStatus(tenantId, targetId, PeriodStatus.HARD_CLOSED, "admin"))
                .isInstanceOf(InvalidPeriodTransitionException.class);
    }

    @Test
    @DisplayName("changeStatus should run revaluation and persist on SOFT_CLOSED to HARD_CLOSED")
    void changeStatusShouldRunRevaluationOnHardClose() {
        UUID tenantId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        AccountingPeriod target = AccountingPeriod.builder()
                .periodId(targetId)
                .tenantId(tenantId)
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .status(PeriodStatus.SOFT_CLOSED)
                .build();
        when(accountingPeriodRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(accountingPeriodRepository.findByTenantIdOrderByStartDateAsc(tenantId)).thenReturn(List.of(target));
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.setField(service, "defaultReserveAccountCode", "R");
        ReflectionTestUtils.setField(service, "defaultGainAccountCode", "G");
        ReflectionTestUtils.setField(service, "defaultLossAccountCode", "L");

        var response = service.changeStatus(tenantId, targetId, PeriodStatus.HARD_CLOSED, "admin");

        assertThat(response.getStatus()).isEqualTo(PeriodStatus.HARD_CLOSED);
        verify(periodEndRevaluationService).run(any(), any(), any());
        verify(auditService).logChange(any(), any(), any(), any(), any(), any(), any());
    }
}

