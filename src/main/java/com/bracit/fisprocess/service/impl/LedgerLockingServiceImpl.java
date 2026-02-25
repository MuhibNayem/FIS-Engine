package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.service.LedgerLockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Pessimistic locking service using SELECT ... FOR UPDATE for hot account
 * safety.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerLockingServiceImpl implements LedgerLockingService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateAccountBalance(UUID accountId, Long deltaAmountCents) {
        // Lock the account row with SELECT FOR UPDATE and update balance
        int updated = accountRepository.lockAndUpdateBalance(accountId, deltaAmountCents);
        if (updated == 0) {
            throw new IllegalStateException("Account " + accountId + " not found for balance update");
        }
        log.debug("Updated balance for account '{}' by {} cents", accountId, deltaAmountCents);
    }
}
