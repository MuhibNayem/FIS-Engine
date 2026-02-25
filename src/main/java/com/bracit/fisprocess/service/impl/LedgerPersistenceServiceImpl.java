package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.HashChainService;
import com.bracit.fisprocess.service.LedgerLockingService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Atomic persistence of journal entries with balance updates and hash chain
 * computation.
 * <p>
 * All operations occur within a single {@code @Transactional} boundary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerPersistenceServiceImpl implements LedgerPersistenceService {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final HashChainService hashChainService;
    private final LedgerLockingService ledgerLockingService;

    @Override
    @Transactional
    public JournalEntry persist(DraftJournalEntry draft) {
        // 1. Get the previous hash for chain continuity
        String previousHash = hashChainService.getLatestHash(draft.getTenantId());

        // Precompute immutable identity fields so the row is insert-only.
        UUID journalEntryId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        String hash = hashChainService.computeHash(journalEntryId, previousHash, createdAt);

        // 2. Build the JournalEntry entity
        JournalEntry journalEntry = JournalEntry.builder()
                .id(journalEntryId)
                .tenantId(draft.getTenantId())
                .eventId(draft.getEventId())
                .postedDate(draft.getPostedDate())
                .description(draft.getDescription())
                .referenceId(draft.getReferenceId())
                .status(draft.getReversalOfId() != null ? JournalStatus.REVERSAL : JournalStatus.POSTED)
                .reversalOfId(draft.getReversalOfId())
                .transactionCurrency(draft.getTransactionCurrency())
                .baseCurrency(draft.getBaseCurrency())
                .exchangeRate(draft.getExchangeRate())
                .createdBy(draft.getCreatedBy())
                .createdAt(createdAt)
                .previousHash(previousHash)
                .hash(hash)
                .build();

        // 3. Add journal lines
        for (DraftJournalLine draftLine : draft.getLines()) {
            Account account = accountRepository
                    .findByTenantIdAndCode(draft.getTenantId(), draftLine.getAccountCode())
                    .orElseThrow(() -> new AccountNotFoundException(draftLine.getAccountCode()));

            JournalLine line = JournalLine.builder()
                    .account(account)
                    .amount(draftLine.getAmountCents())
                    .baseAmount(draftLine.getBaseAmountCents() != null
                            ? draftLine.getBaseAmountCents()
                            : draftLine.getAmountCents())
                    .isCredit(draftLine.isCredit())
                    .dimensions(draftLine.getDimensions())
                    .build();

            journalEntry.addLine(line);
        }

        // 4. Persist immutably (no follow-up UPDATE on ledger row)
        journalEntry = journalEntryRepository.save(journalEntry);

        // 5. Update account balances with deterministic lock ordering
        updateAccountBalances(draft);

        log.info("Persisted JournalEntry '{}' for tenant '{}' with {} lines",
                journalEntry.getId(), draft.getTenantId(), draft.getLines().size());

        return journalEntry;
    }

    /**
     * Updates account balances with deterministic lock ordering (sorted by account
     * code)
     * to prevent deadlocks under concurrent multi-account postings.
     */
    private void updateAccountBalances(DraftJournalEntry draft) {
        // Sort lines by account code for deterministic lock ordering
        List<DraftJournalLine> sortedLines = draft.getLines().stream()
                .sorted(Comparator.comparing(DraftJournalLine::getAccountCode))
                .toList();

        for (DraftJournalLine line : sortedLines) {
            Account account = accountRepository
                    .findByTenantIdAndCode(draft.getTenantId(), line.getAccountCode())
                    .orElseThrow(() -> new AccountNotFoundException(line.getAccountCode()));

            long delta = computeBalanceDelta(account.getAccountType(), line.getAmountCents(), line.isCredit());
            ledgerLockingService.updateAccountBalance(account.getAccountId(), delta);
        }
    }

    /**
     * Computes the balance delta based on account type and debit/credit direction.
     * <p>
     * Normal balances:
     * <ul>
     * <li>ASSET, EXPENSE: Debit increases, Credit decreases</li>
     * <li>LIABILITY, EQUITY, REVENUE: Credit increases, Debit decreases</li>
     * </ul>
     */
    private long computeBalanceDelta(AccountType accountType, long amount, boolean isCredit) {
        boolean isNormalDebit = accountType == AccountType.ASSET || accountType == AccountType.EXPENSE;

        if (isNormalDebit) {
            return isCredit ? -amount : amount;
        } else {
            return isCredit ? amount : -amount;
        }
    }
}
