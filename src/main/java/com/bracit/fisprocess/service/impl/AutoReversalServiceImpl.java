package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.exception.AccountingPeriodNotFoundException;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.AutoReversalService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link AutoReversalService}.
 * <p>
 * When a period opens, this service finds all auto-reversible JEs from the
 * <em>prior</em> period and generates mirror reversals dated to the first day
 * of the newly opened period.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoReversalServiceImpl implements AutoReversalService {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final LedgerPersistenceService ledgerPersistenceService;
    private final OutboxService outboxService;

    @Override
    @Transactional
    public int generateReversals(UUID tenantId, UUID periodId, String createdBy) {
        // 1. Resolve the current (newly opened) period
        AccountingPeriod currentPeriod = accountingPeriodRepository.findById(periodId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(AccountingPeriodNotFoundException::new);

        // 2. Find the prior period (the one immediately before this period)
        List<AccountingPeriod> allPeriods = accountingPeriodRepository
                .findByTenantIdOrderByStartDateAsc(tenantId);

        AccountingPeriod priorPeriod = null;
        for (int i = 0; i < allPeriods.size(); i++) {
            if (allPeriods.get(i).getPeriodId().equals(periodId) && i > 0) {
                priorPeriod = allPeriods.get(i - 1);
                break;
            }
        }

        if (priorPeriod == null) {
            log.info("No prior period found for period '{}'. Skipping auto-reversals.", periodId);
            return 0;
        }

        // 3. Find all auto-reverse JEs posted in the prior period that haven't been
        // reversed
        List<JournalEntry> autoReverseEntries = journalEntryRepository.findAutoReverseEntries(
                tenantId, priorPeriod.getStartDate(), priorPeriod.getEndDate());

        if (autoReverseEntries.isEmpty()) {
            log.info("No auto-reversible entries found for prior period '{}'.", priorPeriod.getName());
            return 0;
        }

        // 4. Generate reversal for each entry
        int reversalCount = 0;
        for (JournalEntry original : autoReverseEntries) {
            // Load lines eagerly
            JournalEntry withLines = journalEntryRepository
                    .findWithLinesByTenantIdAndId(tenantId, original.getId())
                    .orElse(original);

            String reversalEventId = "AUTO-REVERSE:" + withLines.getId();

            // Skip if reversal already exists (idempotency)
            if (journalEntryRepository.existsByTenantIdAndEventId(tenantId, reversalEventId)) {
                log.debug("Reversal already exists for JE '{}', skipping.", withLines.getId());
                continue;
            }

            DraftJournalEntry reversalDraft = DraftJournalEntry.builder()
                    .tenantId(tenantId)
                    .eventId(reversalEventId)
                    .postedDate(currentPeriod.getStartDate())
                    .effectiveDate(currentPeriod.getStartDate())
                    .transactionDate(currentPeriod.getStartDate())
                    .description("Auto-reversal of accrual JE " + withLines.getId())
                    .referenceId(withLines.getReferenceId())
                    .transactionCurrency(withLines.getTransactionCurrency())
                    .baseCurrency(withLines.getBaseCurrency())
                    .exchangeRate(withLines.getExchangeRate())
                    .createdBy(createdBy)
                    .reversalOfId(withLines.getId())
                    .autoReverse(false)
                    .lines(withLines.getLines().stream()
                            .map(line -> DraftJournalLine.builder()
                                    .accountCode(line.getAccount().getCode())
                                    .amountCents(line.getAmount())
                                    .baseAmountCents(line.getBaseAmount())
                                    .isCredit(!line.isCredit()) // Flip DR/CR
                                    .dimensions(line.getDimensions())
                                    .build())
                            .toList())
                    .build();

            JournalEntry reversal = ledgerPersistenceService.persist(reversalDraft);
            outboxService.recordJournalPosted(tenantId, reversalEventId, reversal, null);
            reversalCount++;

            log.info("Generated auto-reversal JE '{}' for original JE '{}' dated {}",
                    reversal.getId(), withLines.getId(), currentPeriod.getStartDate());
        }

        log.info("Auto-reversal complete for tenant '{}': {} reversals generated for period '{}'.",
                tenantId, reversalCount, currentPeriod.getName());
        return reversalCount;
    }
}
