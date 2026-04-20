package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.service.LedgerLockingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerLockingServiceImpl Unit Tests")
class LedgerLockingServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @InjectMocks private LedgerLockingServiceImpl service;

    @Test
    @DisplayName("should update balance when account exists")
    void shouldUpdateBalance() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.lockAndUpdateBalance(accountId, 500L)).thenReturn(1);

        service.updateAccountBalance(accountId, 500L);

        org.mockito.Mockito.verify(accountRepository).lockAndUpdateBalance(accountId, 500L);
    }

    @Test
    @DisplayName("should throw when account not found for balance update")
    void shouldThrowWhenAccountNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.lockAndUpdateBalance(accountId, -200L)).thenReturn(0);

        assertThatThrownBy(() -> service.updateAccountBalance(accountId, -200L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("should handle negative delta (credit)")
    void shouldHandleNegativeDelta() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.lockAndUpdateBalance(accountId, -1000L)).thenReturn(1);

        service.updateAccountBalance(accountId, -1000L);
    }

    @Test
    @DisplayName("should handle zero delta")
    void shouldHandleZeroDelta() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.lockAndUpdateBalance(accountId, 0L)).thenReturn(1);

        service.updateAccountBalance(accountId, 0L);
    }
}
