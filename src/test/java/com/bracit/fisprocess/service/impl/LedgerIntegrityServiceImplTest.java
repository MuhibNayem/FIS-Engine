package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.dto.response.LedgerIntegrityCheckResponseDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerIntegrityServiceImpl Unit Tests")
class LedgerIntegrityServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;

    @InjectMocks
    private LedgerIntegrityServiceImpl service;

    @Test
    @DisplayName("checkTenant should return balanced when accounting equation holds")
    void checkTenantShouldReturnBalancedWhenEquationHolds() {
        UUID tenantId = UUID.randomUUID();
        when(accountRepository.sumBalancesByType(tenantId)).thenReturn(List.of(
                new Object[]{"ASSET", 1000L},
                new Object[]{"LIABILITY", 400L},
                new Object[]{"EQUITY", 300L},
                new Object[]{"REVENUE", 500L},
                new Object[]{"EXPENSE", 200L}
        ));

        LedgerIntegrityCheckResponseDto result = service.checkTenant(tenantId);

        assertThat(result.isBalanced()).isTrue();
        assertThat(result.getEquationDelta()).isZero();
    }

    @Test
    @DisplayName("checkTenant should return failed when accounting equation is broken")
    void checkTenantShouldReturnFailedWhenEquationBroken() {
        UUID tenantId = UUID.randomUUID();
        when(accountRepository.sumBalancesByType(tenantId)).thenReturn(List.of(
                new Object[]{"ASSET", 1000L},
                new Object[]{"LIABILITY", 200L},
                new Object[]{"EQUITY", 300L},
                new Object[]{"REVENUE", 300L},
                new Object[]{"EXPENSE", 100L}
        ));

        LedgerIntegrityCheckResponseDto result = service.checkTenant(tenantId);

        assertThat(result.isBalanced()).isFalse();
        assertThat(result.getEquationDelta()).isEqualTo(300L);
    }

    @Test
    @DisplayName("scheduledIntegrityCheck should process active tenants")
    void scheduledIntegrityCheckShouldProcessActiveTenants() {
        UUID tenantId = UUID.randomUUID();
        when(businessEntityRepository.findAll()).thenReturn(List.of(
                BusinessEntity.builder().tenantId(tenantId).isActive(true).build()
        ));
        when(accountRepository.sumBalancesByType(tenantId)).thenReturn(List.<Object[]>of(
                new Object[]{"ASSET", 0L}
        ));

        service.scheduledIntegrityCheck();
    }
}
