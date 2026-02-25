package com.bracit.fisprocess.service;

import java.util.UUID;

/**
 * Acquires pessimistic locks on account rows and atomically updates balances.
 */
public interface LedgerLockingService {

    /**
     * Acquires a {@code SELECT ... FOR UPDATE} lock on the account row
     * and adds the delta to {@code current_balance}.
     *
     * @param accountId        the account to lock and update
     * @param deltaAmountCents positive to increase, negative to decrease
     */
    void updateAccountBalance(UUID accountId, Long deltaAmountCents);
}
