package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.enums.ActorRole;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.exception.AccountingPeriodNotFoundException;
import com.bracit.fisprocess.exception.PeriodClosedException;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PeriodValidationServiceImpl Unit Tests")
class PeriodValidationServiceImplTest {

    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;

    @InjectMocks
    private PeriodValidationServiceImpl service;

    @Test
    @DisplayName("validatePostingAllowed should allow SOFT_CLOSED for admin")
    void shouldAllowSoftClosedForAdmin() {
        UUID tenantId = UUID.randomUUID();
        LocalDate postedDate = LocalDate.of(2026, 2, 20);
        when(accountingPeriodRepository.findContainingDate(tenantId, postedDate))
                .thenReturn(Optional.of(AccountingPeriod.builder().status(PeriodStatus.SOFT_CLOSED).build()));

        assertThatCode(() -> service.validatePostingAllowed(tenantId, postedDate, ActorRole.FIS_ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePostingAllowed should reject HARD_CLOSED")
    void shouldRejectHardClosed() {
        UUID tenantId = UUID.randomUUID();
        LocalDate postedDate = LocalDate.of(2026, 2, 20);
        when(accountingPeriodRepository.findContainingDate(tenantId, postedDate))
                .thenReturn(Optional.of(AccountingPeriod.builder().status(PeriodStatus.HARD_CLOSED).build()));

        assertThatThrownBy(() -> service.validatePostingAllowed(tenantId, postedDate, ActorRole.FIS_ACCOUNTANT))
                .isInstanceOf(PeriodClosedException.class);
    }

    @Test
    @DisplayName("validatePostingAllowed should reject when period missing")
    void shouldRejectWhenPeriodMissing() {
        UUID tenantId = UUID.randomUUID();
        LocalDate postedDate = LocalDate.of(2026, 2, 20);
        when(accountingPeriodRepository.findContainingDate(tenantId, postedDate)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validatePostingAllowed(tenantId, postedDate, ActorRole.FIS_ACCOUNTANT))
                .isInstanceOf(AccountingPeriodNotFoundException.class);
    }
}

