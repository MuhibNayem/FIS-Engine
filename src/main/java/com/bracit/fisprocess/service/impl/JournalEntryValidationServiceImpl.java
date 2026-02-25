package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.InactiveAccountException;
import com.bracit.fisprocess.exception.UnbalancedEntryException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.service.JournalEntryValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Validates that a draft Journal Entry satisfies double-entry accounting rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JournalEntryValidationServiceImpl implements JournalEntryValidationService {

    private final AccountRepository accountRepository;

    @Override
    public void validate(DraftJournalEntry draft) {
        validateDebitCreditBalance(draft);
        validateAccountsExistAndActive(draft);
    }

    private void validateDebitCreditBalance(DraftJournalEntry draft) {
        long totalDebits = draft.getLines().stream()
                .filter(line -> !line.isCredit())
                .mapToLong(DraftJournalLine::getAmountCents)
                .sum();

        long totalCredits = draft.getLines().stream()
                .filter(DraftJournalLine::isCredit)
                .mapToLong(DraftJournalLine::getAmountCents)
                .sum();

        if (totalDebits != totalCredits) {
            throw new UnbalancedEntryException(totalDebits, totalCredits);
        }

        if (totalDebits == 0) {
            throw new UnbalancedEntryException(0, 0);
        }
    }

    private void validateAccountsExistAndActive(DraftJournalEntry draft) {
        for (DraftJournalLine line : draft.getLines()) {
            Account account = accountRepository
                    .findByTenantIdAndCode(draft.getTenantId(), line.getAccountCode())
                    .orElseThrow(() -> new AccountNotFoundException(line.getAccountCode()));

            if (!account.isActive()) {
                throw new InactiveAccountException(line.getAccountCode());
            }
        }
    }
}
