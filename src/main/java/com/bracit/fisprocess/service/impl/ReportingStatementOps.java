package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.dto.response.BalanceSheetLineDto;
import com.bracit.fisprocess.dto.response.BalanceSheetReportDto;
import com.bracit.fisprocess.dto.response.BalanceSheetSectionDto;
import com.bracit.fisprocess.dto.response.IncomeStatementLineDto;
import com.bracit.fisprocess.dto.response.IncomeStatementReportDto;
import com.bracit.fisprocess.dto.response.IncomeStatementSectionDto;
import com.bracit.fisprocess.dto.response.ReportMetadataDto;
import com.bracit.fisprocess.dto.response.TrialBalanceLineDto;
import com.bracit.fisprocess.dto.response.TrialBalanceReportDto;
import com.bracit.fisprocess.exception.ReportParameterException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.ReportingRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ReportingStatementOps {

    private ReportingStatementOps() {
    }

    static TrialBalanceReportDto generateTrialBalance(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            LocalDate asOfDate) {
        String baseCurrency = resolveBaseCurrency(businessEntityRepository, tenantId);
        List<Map<String, Object>> rows = reportingRepository.findTrialBalanceLines(tenantId, asOfDate);

        List<TrialBalanceLineDto> lines = rows.stream()
                .map(row -> TrialBalanceLineDto.builder()
                        .accountCode(str(row, "account_code"))
                        .accountName(str(row, "account_name"))
                        .accountType(str(row, "account_type"))
                        .debitBalance(lng(row, "total_debits"))
                        .creditBalance(lng(row, "total_credits"))
                        .build())
                .toList();

        long totalDebits = lines.stream().mapToLong(TrialBalanceLineDto::getDebitBalance).sum();
        long totalCredits = lines.stream().mapToLong(TrialBalanceLineDto::getCreditBalance).sum();

        return TrialBalanceReportDto.builder()
                .metadata(metadata("TRIAL_BALANCE", tenantId, baseCurrency, asOfDate, null, null))
                .lines(lines)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .balanced(totalDebits == totalCredits)
                .build();
    }

    static BalanceSheetReportDto generateBalanceSheet(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            LocalDate asOfDate) {
        String baseCurrency = resolveBaseCurrency(businessEntityRepository, tenantId);
        List<Map<String, Object>> rows = reportingRepository.findBalanceSheetAccounts(tenantId, asOfDate);

        Map<String, List<BalanceSheetLineDto>> sections = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String type = str(row, "account_type");
            long rawBalance = lng(row, "net_balance");
            boolean creditNormal = "LIABILITY".equals(type) || "EQUITY".equals(type);
            long balance = creditNormal ? -rawBalance : rawBalance;
            sections.computeIfAbsent(type, k -> new ArrayList<>())
                    .add(BalanceSheetLineDto.builder()
                            .accountCode(str(row, "account_code"))
                            .accountName(str(row, "account_name"))
                            .balanceCents(balance)
                            .formattedBalance(formatCents(balance))
                            .build());
        }

        BalanceSheetSectionDto assetSection = buildSection("Assets", sections.getOrDefault("ASSET", List.of()));
        BalanceSheetSectionDto liabilitySection = buildSection("Liabilities", sections.getOrDefault("LIABILITY", List.of()));
        BalanceSheetSectionDto equitySection = buildSection("Equity", sections.getOrDefault("EQUITY", List.of()));

        long totalAssets = assetSection.getSectionTotal();
        long totalLiabilitiesAndEquity = liabilitySection.getSectionTotal() + equitySection.getSectionTotal();

        return BalanceSheetReportDto.builder()
                .metadata(metadata("BALANCE_SHEET", tenantId, baseCurrency, asOfDate, null, null))
                .assets(assetSection)
                .liabilities(liabilitySection)
                .equity(equitySection)
                .totalAssets(totalAssets)
                .totalLiabilitiesAndEquity(totalLiabilitiesAndEquity)
                .balanced(totalAssets == totalLiabilitiesAndEquity)
                .build();
    }

    static IncomeStatementReportDto generateIncomeStatement(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        String baseCurrency = resolveBaseCurrency(businessEntityRepository, tenantId);
        List<Map<String, Object>> rows = reportingRepository.findIncomeStatementAccounts(tenantId, fromDate, toDate);

        List<IncomeStatementLineDto> revenueLines = new ArrayList<>();
        List<IncomeStatementLineDto> expenseLines = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String type = str(row, "account_type");
            long amount = Math.abs(lng(row, "net_amount"));
            IncomeStatementLineDto line = IncomeStatementLineDto.builder()
                    .accountCode(str(row, "account_code"))
                    .accountName(str(row, "account_name"))
                    .amountCents(amount)
                    .formattedAmount(formatCents(amount))
                    .build();
            if ("REVENUE".equals(type)) {
                revenueLines.add(line);
            } else {
                expenseLines.add(line);
            }
        }

        long totalRevenue = revenueLines.stream().mapToLong(IncomeStatementLineDto::getAmountCents).sum();
        long totalExpenses = expenseLines.stream().mapToLong(IncomeStatementLineDto::getAmountCents).sum();

        return IncomeStatementReportDto.builder()
                .metadata(metadata("INCOME_STATEMENT", tenantId, baseCurrency, null, fromDate, toDate))
                .revenue(IncomeStatementSectionDto.builder()
                        .sectionName("Revenue")
                        .lines(revenueLines)
                        .sectionTotal(totalRevenue)
                        .build())
                .expenses(IncomeStatementSectionDto.builder()
                        .sectionName("Expenses")
                        .lines(expenseLines)
                        .sectionTotal(totalExpenses)
                        .build())
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .netIncome(totalRevenue - totalExpenses)
                .build();
    }

    static String resolveBaseCurrency(BusinessEntityRepository businessEntityRepository, UUID tenantId) {
        BusinessEntity tenant = businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
        return tenant.getBaseCurrency();
    }

    static void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new ReportParameterException("Both fromDate and toDate are required.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ReportParameterException("fromDate must not be after toDate.");
        }
    }

    static ReportMetadataDto metadata(String reportType, UUID tenantId, String baseCurrency,
            LocalDate asOfDate, LocalDate fromDate, LocalDate toDate) {
        return ReportMetadataDto.builder()
                .reportType(reportType)
                .tenantId(tenantId)
                .asOfDate(asOfDate)
                .fromDate(fromDate)
                .toDate(toDate)
                .baseCurrency(baseCurrency)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    private static BalanceSheetSectionDto buildSection(String name, List<BalanceSheetLineDto> lines) {
        long total = lines.stream().mapToLong(BalanceSheetLineDto::getBalanceCents).sum();
        return BalanceSheetSectionDto.builder().sectionName(name).lines(lines).sectionTotal(total).build();
    }

    static String str(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val != null ? val.toString() : "";
    }

    static long lng(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof Number num) {
            return num.longValue();
        }
        return 0L;
    }

    static Long lngOrNull(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    static UUID uuid(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof UUID u) {
            return u;
        }
        return val != null ? UUID.fromString(val.toString()) : null;
    }

    static LocalDate date(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof LocalDate ld) {
            return ld;
        }
        if (val instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return val != null ? LocalDate.parse(val.toString()) : null;
    }

    static String formatCents(long cents) {
        long wholePart = cents / 100;
        long fracPart = Math.abs(cents % 100);
        return String.format("%d.%02d", wholePart, fracPart);
    }
}
