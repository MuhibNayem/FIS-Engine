package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.dto.response.AccountActivityReportDto;
import com.bracit.fisprocess.dto.response.DimensionSummaryLineDto;
import com.bracit.fisprocess.dto.response.DimensionSummaryReportDto;
import com.bracit.fisprocess.dto.response.GeneralLedgerEntryDto;
import com.bracit.fisprocess.dto.response.GeneralLedgerReportDto;
import com.bracit.fisprocess.dto.response.JournalRegisterEntryDto;
import com.bracit.fisprocess.dto.response.JournalRegisterReportDto;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.ReportParameterException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.ReportingRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ReportingLedgerOps {

    private ReportingLedgerOps() {
    }

    static GeneralLedgerReportDto generateGeneralLedger(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            String accountCode,
            LocalDate fromDate,
            LocalDate toDate) {
        ReportingStatementOps.validateDateRange(fromDate, toDate);
        validateAccountExists(reportingRepository, tenantId, accountCode);
        String baseCurrency = ReportingStatementOps.resolveBaseCurrency(businessEntityRepository, tenantId);
        String accountName = reportingRepository.findAccountName(tenantId, accountCode).orElse(accountCode);

        long openingBalance = reportingRepository.computeOpeningBalance(tenantId, accountCode, fromDate);
        List<Map<String, Object>> rows = reportingRepository.findGeneralLedgerEntries(tenantId, accountCode, fromDate, toDate);

        long runningBalance = openingBalance;
        List<GeneralLedgerEntryDto> entries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long debit = ReportingStatementOps.lng(row, "debit_amount");
            long credit = ReportingStatementOps.lng(row, "credit_amount");
            runningBalance += (debit - credit);

            entries.add(GeneralLedgerEntryDto.builder()
                    .journalEntryId(ReportingStatementOps.uuid(row, "journal_entry_id"))
                    .sequenceNumber(ReportingStatementOps.lngOrNull(row, "sequence_number"))
                    .postedDate(ReportingStatementOps.date(row, "posted_date"))
                    .description(ReportingStatementOps.str(row, "description"))
                    .debitAmount(debit)
                    .creditAmount(credit)
                    .runningBalance(runningBalance)
                    .build());
        }

        return GeneralLedgerReportDto.builder()
                .metadata(ReportingStatementOps.metadata("GENERAL_LEDGER", tenantId, baseCurrency, null, fromDate, toDate))
                .accountCode(accountCode)
                .accountName(accountName)
                .openingBalance(openingBalance)
                .entries(entries)
                .closingBalance(runningBalance)
                .build();
    }

    static AccountActivityReportDto generateAccountActivity(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            String accountCode,
            LocalDate fromDate,
            LocalDate toDate) {
        ReportingStatementOps.validateDateRange(fromDate, toDate);
        validateAccountExists(reportingRepository, tenantId, accountCode);
        String baseCurrency = ReportingStatementOps.resolveBaseCurrency(businessEntityRepository, tenantId);
        String accountName = reportingRepository.findAccountName(tenantId, accountCode).orElse(accountCode);

        long openingBalance = reportingRepository.computeOpeningBalance(tenantId, accountCode, fromDate);
        Map<String, Object> activity = reportingRepository.findAccountActivity(tenantId, accountCode, fromDate, toDate);

        long totalDebits = ReportingStatementOps.lng(activity, "total_debits");
        long totalCredits = ReportingStatementOps.lng(activity, "total_credits");
        long txCount = ReportingStatementOps.lng(activity, "transaction_count");

        return AccountActivityReportDto.builder()
                .metadata(ReportingStatementOps.metadata("ACCOUNT_ACTIVITY", tenantId, baseCurrency, null, fromDate, toDate))
                .accountCode(accountCode)
                .accountName(accountName)
                .openingBalance(openingBalance)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .closingBalance(openingBalance + totalDebits - totalCredits)
                .transactionCount(txCount)
                .build();
    }

    static JournalRegisterReportDto generateJournalRegister(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {
        ReportingStatementOps.validateDateRange(fromDate, toDate);
        if (size <= 0 || size > 1000) {
            throw new ReportParameterException("Page size must be between 1 and 1000.");
        }
        String baseCurrency = ReportingStatementOps.resolveBaseCurrency(businessEntityRepository, tenantId);

        int offset = page * size;
        List<Map<String, Object>> rows = reportingRepository.findJournalRegister(tenantId, fromDate, toDate, offset, size);
        long totalEntries = reportingRepository.countJournalRegister(tenantId, fromDate, toDate);

        List<JournalRegisterEntryDto> entries = rows.stream()
                .map(row -> JournalRegisterEntryDto.builder()
                        .journalEntryId(ReportingStatementOps.uuid(row, "journal_entry_id"))
                        .sequenceNumber(ReportingStatementOps.lngOrNull(row, "sequence_number"))
                        .postedDate(ReportingStatementOps.date(row, "posted_date"))
                        .description(ReportingStatementOps.str(row, "description"))
                        .status(ReportingStatementOps.str(row, "status"))
                        .totalDebits(ReportingStatementOps.lng(row, "total_debits"))
                        .totalCredits(ReportingStatementOps.lng(row, "total_credits"))
                        .createdBy(ReportingStatementOps.str(row, "created_by"))
                        .build())
                .toList();

        return JournalRegisterReportDto.builder()
                .metadata(ReportingStatementOps.metadata("JOURNAL_REGISTER", tenantId, baseCurrency, null, fromDate, toDate))
                .entries(entries)
                .totalEntries(totalEntries)
                .page(page)
                .size(size)
                .totalPages((totalEntries + size - 1) / size)
                .build();
    }

    static DimensionSummaryReportDto generateDimensionSummary(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            String dimensionKey,
            LocalDate fromDate,
            LocalDate toDate) {
        ReportingStatementOps.validateDateRange(fromDate, toDate);
        if (dimensionKey == null || dimensionKey.isBlank()) {
            throw new ReportParameterException("dimensionKey is required.");
        }
        String baseCurrency = ReportingStatementOps.resolveBaseCurrency(businessEntityRepository, tenantId);

        List<Map<String, Object>> rows = reportingRepository.findDimensionSummary(tenantId, dimensionKey, fromDate, toDate);
        List<DimensionSummaryLineDto> lines = rows.stream()
                .map(row -> DimensionSummaryLineDto.builder()
                        .dimensionValue(ReportingStatementOps.str(row, "dimension_value"))
                        .debitTotal(ReportingStatementOps.lng(row, "debit_total"))
                        .creditTotal(ReportingStatementOps.lng(row, "credit_total"))
                        .netAmount(ReportingStatementOps.lng(row, "net_amount"))
                        .build())
                .toList();

        long grandTotal = lines.stream().mapToLong(DimensionSummaryLineDto::getNetAmount).sum();

        return DimensionSummaryReportDto.builder()
                .metadata(ReportingStatementOps.metadata("DIMENSION_SUMMARY", tenantId, baseCurrency, null, fromDate, toDate))
                .dimensionKey(dimensionKey)
                .lines(lines)
                .grandTotal(grandTotal)
                .build();
    }

    private static void validateAccountExists(ReportingRepository reportingRepository, UUID tenantId, String accountCode) {
        if (!reportingRepository.accountExists(tenantId, accountCode)) {
            throw new AccountNotFoundException(accountCode);
        }
    }
}
