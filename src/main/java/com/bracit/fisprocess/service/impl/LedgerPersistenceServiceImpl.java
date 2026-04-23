package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.UnbalancedEntryException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BatchJournalRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.repository.JournalSequenceRepository;
import com.bracit.fisprocess.service.HashChainService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerPersistenceServiceImpl implements LedgerPersistenceService {

        private final JournalEntryRepository journalEntryRepository;
        private final AccountRepository accountRepository;
        private final JournalSequenceRepository journalSequenceRepository;
        private final HashChainService hashChainService;
        private final BatchJournalRepository batchJournalRepository;
        private final MeterRegistry meterRegistry;

        @Value("${fis.batch.enabled:false}")
        private boolean batchEnabled;

        @Value("${fis.batch.use-copy:true}")
        private boolean useCopy;

        @Override
        @Transactional
        public JournalEntry persist(DraftJournalEntry draft) {
                validateBalance(draft);
                long lockWaitStartNanos = System.nanoTime();
                int fiscalYear = draft.getPostedDate().getYear();
                long sequenceNumber = allocateSequenceNumber(draft.getTenantId(), fiscalYear);
                long lockWaitNanos = System.nanoTime() - lockWaitStartNanos;
                meterRegistry.timer("fis.hash.chain.lock.wait").record(lockWaitNanos, TimeUnit.NANOSECONDS);
                log.debug("Acquired fiscal-year sequence lock for tenant='{}', fiscalYear='{}' in {} ms",
                                draft.getTenantId(), fiscalYear, lockWaitNanos / 1_000_000.0);

                String previousHash = hashChainService.getLatestHash(draft.getTenantId(), fiscalYear);
                UUID journalEntryId = UUID.randomUUID();
                OffsetDateTime createdAt = OffsetDateTime.now();
                String hash = hashChainService.computeHash(journalEntryId, previousHash, createdAt, draft.getLines());

                JournalEntry journalEntry = buildJournalEntry(draft, journalEntryId, previousHash, hash, fiscalYear, sequenceNumber, createdAt);

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

                journalEntryRepository.save(journalEntry);
                meterRegistry.counter("fis.journal.entries.persisted").increment();

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

                for (DraftJournalEntry draft : drafts) {
                        validateBalance(draft);
                }

                Timer.Sample sample = Timer.start(meterRegistry);
                List<JournalEntry> persistedEntries = new ArrayList<>();
                Map<SequenceKey, SequenceAllocation> allocatedSequences = new ConcurrentHashMap<>();

                try {
                        allocateSequenceRanges(drafts, allocatedSequences);

                        List<JournalEntry> entriesToSave = buildJournalEntries(drafts, allocatedSequences);

                        if (batchEnabled) {
                                if (useCopy) {
                                        batchJournalRepository.copyFromEntries(entriesToSave);
                                } else {
                                        batchJournalRepository.batchInsertEntries(entriesToSave);
                                }
                        } else {
                                for (JournalEntry entry : entriesToSave) {
                                        journalEntryRepository.save(entry);
                                }
                        }

                        markSequencesCommitted(allocatedSequences);

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

        private void allocateSequenceRanges(List<DraftJournalEntry> drafts, Map<SequenceKey, SequenceAllocation> allocatedSequences) {
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

                        SequenceAllocation alloc = allocatedSequences.get(key);
                        alloc.used++;
                }

                for (Map.Entry<SequenceKey, SequenceAllocation> entry : allocatedSequences.entrySet()) {
                        SequenceKey key = entry.getKey();
                        SequenceAllocation alloc = entry.getValue();
                        journalSequenceRepository.findForUpdate(key.tenantId, key.fiscalYear)
                                .ifPresent(seq -> {
                                        seq.setNextValue(alloc.startSeq + alloc.used);
                                        journalSequenceRepository.save(seq);
                                });
                }
        }

        private List<JournalEntry> buildJournalEntries(List<DraftJournalEntry> drafts, Map<SequenceKey, SequenceAllocation> allocatedSequences) {
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

                return entriesToSave;
        }

        private void markSequencesCommitted(Map<SequenceKey, SequenceAllocation> allocatedSequences) {
                for (SequenceAllocation alloc : allocatedSequences.values()) {
                        alloc.committed = true;
                }
        }

        private void validateBalance(DraftJournalEntry draft) {
                long totalDebits = draft.getLines().stream()
                                .filter(l -> !l.isCredit())
                                .mapToLong(l -> l.getBaseAmountCents() != null ? l.getBaseAmountCents() : l.getAmountCents())
                                .sum();
                long totalCredits = draft.getLines().stream()
                                .filter(DraftJournalLine::isCredit)
                                .mapToLong(l -> l.getBaseAmountCents() != null ? l.getBaseAmountCents() : l.getAmountCents())
                                .sum();

                if (totalDebits != totalCredits) {
                        throw new UnbalancedEntryException(totalDebits, totalCredits);
                }

                if (totalDebits == 0) {
                        throw new UnbalancedEntryException("Journal entry has zero debits and credits");
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
}