package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.dto.request.YearEndCloseRequestDto;
import com.bracit.fisprocess.dto.response.YearEndCloseResponseDto;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.YearEndCloseException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.OutboxService;
import com.bracit.fisprocess.service.YearEndCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link YearEndCloseService}.
 * <p>
 * Performs the year-end close by:
 * <ol>
 * <li>Validating all periods in the fiscal year are HARD_CLOSED</li>
 * <li>Finding all Revenue/Expense accounts with non-zero balances</li>
 * <li>Generating a closing JE that zeros out P&amp;L and nets to Retained
 * Earnings</li>
 * <li>Persisting the closing JE via the ledger persistence service</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class YearEndCloseServiceImpl implements YearEndCloseService {

    private final AccountRepository accountRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final LedgerPersistenceService ledgerPersistenceService;
    private final OutboxService outboxService;

    @Override
    @Transactional
    public YearEndCloseResponseDto performYearEndClose(UUID tenantId, YearEndCloseRequestDto request) {
        int fiscalYear = request.getFiscalYear();
        String retainedEarningsCode = request.getRetainedEarningsAccountCode();
        String createdBy = request.getCreatedBy();

        // 1. Validate tenant exists
        BusinessEntity tenant = businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        // 2. Validate retained earnings account exists and is EQUITY type
        Account retainedEarnings = accountRepository.findByTenantIdAndCode(tenantId, retainedEarningsCode)
                .orElseThrow(() -> new AccountNotFoundException(retainedEarningsCode));
        if (retainedEarnings.getAccountType() != AccountType.EQUITY) {
            throw new YearEndCloseException("Retained earnings account '" + retainedEarningsCode
                    + "' must be of type EQUITY, but is " + retainedEarnings.getAccountType());
        }

        // 3. Validate all periods in the fiscal year are HARD_CLOSED
        validateAllPeriodsHardClosed(tenantId, fiscalYear);

        // 4. Find all Revenue/Expense accounts with non-zero balances
        List<Account> plAccounts = accountRepository.findByTenantIdAndAccountTypeInAndCurrentBalanceNot(
                tenantId, List.of(AccountType.REVENUE, AccountType.EXPENSE), 0L);

        if (plAccounts.isEmpty()) {
            return YearEndCloseResponseDto.builder()
                    .fiscalYear(fiscalYear)
                    .totalRevenue(0L)
                    .totalExpenses(0L)
                    .netIncome(0L)
                    .accountsClosed(0)
                    .retainedEarningsAccountCode(retainedEarningsCode)
                    .message("No Revenue/Expense accounts with non-zero balances found. Nothing to close.")
                    .build();
        }

        // 5. Compute totals and build closing JE lines
        long totalRevenue = 0L;
        long totalExpenses = 0L;
        List<DraftJournalLine> closingLines = new ArrayList<>();

        for (Account account : plAccounts) {
            long balance = account.getCurrentBalance();

            if (account.getAccountType() == AccountType.REVENUE) {
                // Revenue accounts have credit-normal balances (negative in DR-CR convention)
                // To zero out: we debit them (positive amount, isCredit=false)
                totalRevenue += Math.abs(balance);
                closingLines.add(DraftJournalLine.builder()
                        .accountCode(account.getCode())
                        .amountCents(Math.abs(balance))
                        .baseAmountCents(Math.abs(balance))
                        .isCredit(balance < 0 ? false : true) // Reverse the natural direction
                        .build());
            } else {
                // Expense accounts have debit-normal balances (positive in DR-CR convention)
                // To zero out: we credit them (positive amount, isCredit=true)
                totalExpenses += Math.abs(balance);
                closingLines.add(DraftJournalLine.builder()
                        .accountCode(account.getCode())
                        .amountCents(Math.abs(balance))
                        .baseAmountCents(Math.abs(balance))
                        .isCredit(balance > 0 ? true : false) // Reverse the natural direction
                        .build());
            }
        }

        // 6. Net income line to Retained Earnings
        // Net income = Revenue - Expenses (in absolute terms)
        long netIncome = totalRevenue - totalExpenses;

        // The closing JE must balance. The net goes to Retained Earnings.
        // If netIncome > 0 (profit): Credit Retained Earnings
        // If netIncome < 0 (loss): Debit Retained Earnings
        if (netIncome != 0) {
            closingLines.add(DraftJournalLine.builder()
                    .accountCode(retainedEarningsCode)
                    .amountCents(Math.abs(netIncome))
                    .baseAmountCents(Math.abs(netIncome))
                    .isCredit(netIncome > 0) // Profit → credit; Loss → debit
                    .build());
        }

        // 7. Build and persist the closing journal entry
        String eventId = "YEAR-END-CLOSE:" + fiscalYear + ":" + tenantId;
        LocalDate closingDate = LocalDate.of(fiscalYear, 12, 31);

        DraftJournalEntry closingDraft = DraftJournalEntry.builder()
                .tenantId(tenantId)
                .eventId(eventId)
                .postedDate(closingDate)
                .effectiveDate(closingDate)
                .transactionDate(closingDate)
                .description("Year-end closing entry for fiscal year " + fiscalYear)
                .referenceId("YEC-" + fiscalYear)
                .transactionCurrency(tenant.getBaseCurrency())
                .baseCurrency(tenant.getBaseCurrency())
                .exchangeRate(BigDecimal.ONE)
                .createdBy(createdBy)
                .lines(closingLines)
                .build();

        JournalEntry closingEntry = ledgerPersistenceService.persist(closingDraft);
        outboxService.recordJournalPosted(tenantId, eventId, closingEntry, null);

        log.info("Year-end close completed for tenant '{}', fiscal year {}. Net income: {}, closing JE: {}",
                tenantId, fiscalYear, netIncome, closingEntry.getId());

        return YearEndCloseResponseDto.builder()
                .fiscalYear(fiscalYear)
                .closingJournalEntryId(closingEntry.getId())
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .netIncome(netIncome)
                .accountsClosed(plAccounts.size())
                .retainedEarningsAccountCode(retainedEarningsCode)
                .message("Year-end close completed successfully. Closing JE posted.")
                .build();
    }

    /**
     * Validates that all accounting periods overlapping the fiscal year are
     * HARD_CLOSED.
     */
    private void validateAllPeriodsHardClosed(UUID tenantId, int fiscalYear) {
        LocalDate yearStart = LocalDate.of(fiscalYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(fiscalYear, 12, 31);

        List<AccountingPeriod> periods = accountingPeriodRepository.findOverlapping(tenantId, yearStart, yearEnd);

        if (periods.isEmpty()) {
            throw new YearEndCloseException(
                    "No accounting periods found for fiscal year " + fiscalYear + ". Create periods first.");
        }

        List<String> openPeriods = periods.stream()
                .filter(p -> p.getStatus() != PeriodStatus.HARD_CLOSED)
                .map(p -> p.getName() + " (" + p.getStatus() + ")")
                .toList();

        if (!openPeriods.isEmpty()) {
            throw new YearEndCloseException(
                    "Cannot perform year-end close. The following periods are not HARD_CLOSED: " + openPeriods);
        }
    }
}
