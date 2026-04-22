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
import com.bracit.fisprocess.repository.BatchJournalRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.repository.JournalSequenceRepository;
import com.bracit.fisprocess.service.HashChainService;
import com.bracit.fisprocess.service.LedgerLockingService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
        private final JournalSequenceRepository journalSequenceRepository;
        private final HashChainService hashChainService;
        private final LedgerLockingService ledgerLockingService;
        private final BatchJournalRepository batchJournalRepository;
        private final MeterRegistry meterRegistry;

        @Value("${fis.batch.enabled:false}")
        private boolean batchEnabled;

        @Override
        @Transactional
        public JournalEntry persist(DraftJournalEntry draft) {
                long lockWaitStartNanos = System.nanoTime();
                // Serialize sequence + hash-chain writes on tenant/fiscal-year key.
                int fiscalYear = draft.getPostedDate().getYear();
                long sequenceNumber = allocateSequenceNumber(draft.getTenantId(), fiscalYear);
                long lockWaitNanos = System.nanoTime() - lockWaitStartNanos;
                meterRegistry.timer("fis.hash.chain.lock.wait").record(lockWaitNanos, TimeUnit.NANOSECONDS);
                meterRegistry.timer("fis.hash.chain.sequence.lock.wait").record(lockWaitNanos, TimeUnit.NANOSECONDS);
                log.debug("Acquired fiscal-year sequence lock for tenant='{}', fiscalYear='{}' in {} ms",
                                draft.getTenantId(), fiscalYear, lockWaitNanos / 1_000_000.0);

                // 1. Get the previous hash for fiscal-year chain continuity.
                String previousHash = hashChainService.getLatestHash(draft.getTenantId(), fiscalYear);

                // Precompute immutable identity fields so the row is insert-only.
                UUID journalEntryId = UUID.randomUUID();
                OffsetDateTime createdAt = OffsetDateTime.now();
                // Hash now includes journal line content for tamper detection
                String hash = hashChainService.computeHash(journalEntryId, previousHash, createdAt, draft.getLines());

                // 2. Build the JournalEntry entity
                JournalEntry journalEntry = JournalEntry.builder()
                                .id(journalEntryId)
                                .tenantId(draft.getTenantId())
                                .eventId(draft.getEventId())
                                .postedDate(draft.getPostedDate())
                                .effectiveDate(draft.getEffectiveDate())
                                .transactionDate(draft.getTransactionDate())
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
                                .fiscalYear(fiscalYear)
                                .sequenceNumber(sequenceNumber)
                                .autoReverse(draft.isAutoReverse())
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

        @Override
        @Transactional
        public List<JournalEntry> persistBatch(List<DraftJournalEntry> drafts) {
                if (drafts == null || drafts.isEmpty()) {
                        return List.of();
                }

                Timer.Sample sample = Timer.start(meterRegistry);
                List<JournalEntry> persistedEntries = new ArrayList<>();
                Map<SequenceKey, SequenceAllocation> allocatedSequences = new ConcurrentHashMap<>();

                try {
                        for (int i = 0; i < drafts.size(); i++) {
                                DraftJournalEntry draft = drafts.get(i);
                                int fiscalYear = draft.getPostedDate().getYear();
                                SequenceKey key = new SequenceKey(draft.getTenantId(), fiscalYear);

                                if (!allocatedSequences.containsKey(key)) {
                                        journalSequenceRepository.initializeIfAbsent(draft.getTenantId(), fiscalYear);
                                        var sequence = journalSequenceRepository.findForUpdate(draft.getTenantId(), fiscalYear)
                                                .orElseThrow(() -> new IllegalStateException(
                                                        "Journal sequence missing for tenant=%s fiscalYear=%d"
                                                                .formatted(draft.getTenantId(), fiscalYear)));

                                        SequenceAllocation alloc = new SequenceAllocation();
                                        alloc.startSeq = sequence.getNextValue();
                                        alloc.used = 0;
                                        allocatedSequences.put(key, alloc);
                                }
                        }

                        List<JournalEntry> entriesToSave = new ArrayList<>();

                        for (int i = 0; i < drafts.size(); i++) {
                                DraftJournalEntry draft = drafts.get(i);
                                int fiscalYear = draft.getPostedDate().getYear();
                                SequenceKey key = new SequenceKey(draft.getTenantId(), fiscalYear);
                                SequenceAllocation alloc = allocatedSequences.get(key);

                                long sequenceNumber = alloc.startSeq + alloc.used;
                                alloc.used++;

                                String previousHash = hashChainService.getLatestHash(draft.getTenantId(), fiscalYear);
                                UUID journalEntryId = UUID.randomUUID();
                                OffsetDateTime createdAt = OffsetDateTime.now();
                                String hash = hashChainService.computeHash(journalEntryId, previousHash, createdAt, draft.getLines());

                                JournalEntry journalEntry = buildJournalEntry(draft, journalEntryId, previousHash, hash, fiscalYear, sequenceNumber, createdAt);
                                entriesToSave.add(journalEntry);
                        }

                        for (int i = 0; i < drafts.size(); i++) {
                                DraftJournalEntry draft = drafts.get(i);
                                for (DraftJournalLine draftLine : draft.getLines()) {
                                        Account account = accountRepository
                                                        .findByTenantIdAndCode(draft.getTenantId(), draftLine.getAccountCode())
                                                        .orElseThrow(() -> new AccountNotFoundException(draftLine.getAccountCode()));

                                        JournalEntry matchingEntry = entriesToSave.get(i);

                                        JournalLine line = JournalLine.builder()
                                                        .account(account)
                                                        .amount(draftLine.getAmountCents())
                                                        .baseAmount(draftLine.getBaseAmountCents() != null
                                                                        ? draftLine.getBaseAmountCents()
                                                                        : draftLine.getAmountCents())
                                                        .isCredit(draftLine.isCredit())
                                                        .dimensions(draftLine.getDimensions())
                                                        .build();

                                        matchingEntry.addLine(line);
                                }
                        }

                        if (batchEnabled) {
                                batchJournalRepository.batchInsertEntries(entriesToSave);
                        } else {
                                for (JournalEntry entry : entriesToSave) {
                                        journalEntryRepository.save(entry);
                                }
                        }

                        for (DraftJournalEntry draft : drafts) {
                                updateAccountBalances(draft);
                        }

                        for (SequenceAllocation alloc : allocatedSequences.values()) {
                                alloc.committed = true;
                        }

                        persistedEntries.addAll(entriesToSave);

                        meterRegistry.counter("fis.batch.entries.persisted").increment(entriesToSave.size());
                        log.info("Batch persisted {} journal entries", entriesToSave.size());

                        return persistedEntries;

                } catch (Exception e) {
                        log.error("Batch persist failed for {} entries, initiating compensation", drafts.size(), e);
                        meterRegistry.counter("fis.batch.error").increment();

                        compensateSequences(allocatedSequences);

                        throw new RuntimeException("Failed to persist batch: " + e.getMessage(), e);
                } finally {
                        sample.stop(Timer.builder("fis.batch.duration").register(meterRegistry));
                }
        }

        private JournalEntry buildJournalEntry(DraftJournalEntry draft, UUID journalEntryId,
                        String previousHash, String hash, int fiscalYear, long sequenceNumber, OffsetDateTime createdAt) {
                return JournalEntry.builder()
                                .id(journalEntryId)
                                .tenantId(draft.getTenantId())
                                .eventId(draft.getEventId())
                                .postedDate(draft.getPostedDate())
                                .effectiveDate(draft.getEffectiveDate())
                                .transactionDate(draft.getTransactionDate())
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
                                .fiscalYear(fiscalYear)
                                .sequenceNumber(sequenceNumber)
                                .autoReverse(draft.isAutoReverse())
                                .build();
        }

        private void compensateSequences(Map<SequenceKey, SequenceAllocation> allocatedSequences) {
                for (Map.Entry<SequenceKey, SequenceAllocation> entry : allocatedSequences.entrySet()) {
                        SequenceKey key = entry.getKey();
                        SequenceAllocation alloc = entry.getValue();
                        if (!alloc.committed && alloc.used > 0) {
                                try {
                                        journalSequenceRepository.findForUpdate(key.tenantId, key.fiscalYear)
                                                .ifPresent(seq -> {
                                                        seq.setNextValue(seq.getNextValue() - alloc.used);
                                                        journalSequenceRepository.save(seq);
                                                        log.warn("Compensated {} sequences for tenant={}, fiscalYear={}",
                                                                alloc.used, key.tenantId, key.fiscalYear);
                                                });
                                } catch (Exception ex) {
                                        log.error("Failed to compensate sequences for tenant={}, fiscalYear={}: {}",
                                                key.tenantId, key.fiscalYear, ex.getMessage());
                                        meterRegistry.counter("fis.batch.compensation.error").increment();
                                }
                        }
                }
        }

        private static class SequenceKey {
                final UUID tenantId;
                final int fiscalYear;

                SequenceKey(UUID tenantId, int fiscalYear) {
                        this.tenantId = tenantId;
                        this.fiscalYear = fiscalYear;
                }
        }

        private static class SequenceAllocation {
                long startSeq;
                int used;
                boolean committed;
        }

        private long allocateSequenceNumber(UUID tenantId, int fiscalYear) {
                journalSequenceRepository.initializeIfAbsent(tenantId, fiscalYear);
                var sequence = journalSequenceRepository.findForUpdate(tenantId, fiscalYear)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Journal sequence missing for tenant=%s fiscalYear=%d"
                                                                .formatted(tenantId, fiscalYear)));
                long allocated = sequence.getNextValue();
                sequence.setNextValue(allocated + 1);
                journalSequenceRepository.save(sequence);
                return allocated;
        }

        /**
         * Updates account balances with deterministic lock ordering (sorted by account
         * code)
         * to prevent deadlocks under concurrent multi-account postings.
         * <p>
         * CRITICAL: Uses base-currency amounts (baseAmountCents) to ensure account
         * balances are tracked in a consistent currency. Using transaction currency
         * amounts would corrupt balances in multi-currency scenarios.
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

                        // Use base-currency amount for balance consistency across currencies
                        long baseAmountCents = line.getBaseAmountCents() != null
                                        ? line.getBaseAmountCents()
                                        : line.getAmountCents();

                        long delta = computeBalanceDelta(account.getAccountType(), account.isContra(),
                                        baseAmountCents, line.isCredit());
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
        private long computeBalanceDelta(AccountType accountType, boolean isContra, long amount, boolean isCredit) {
                boolean isNormalDebit = accountType == AccountType.ASSET || accountType == AccountType.EXPENSE;
                if (isContra) {
                        isNormalDebit = !isNormalDebit;
                }

                if (isNormalDebit) {
                        return isCredit ? -amount : amount;
                } else {
                        return isCredit ? amount : -amount;
                }
        }
}
