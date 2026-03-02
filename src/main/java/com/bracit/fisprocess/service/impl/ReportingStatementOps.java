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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        Map<String, String> accountNameByCode = new HashMap<>();
        Map<String, String> accountTypeByCode = new HashMap<>();
        Map<String, Long> debitByCode = new HashMap<>();
        Map<String, Long> creditByCode = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String code = str(row, "account_code");
            accountNameByCode.put(code, str(row, "account_name"));
            accountTypeByCode.put(code, str(row, "account_type"));
            debitByCode.put(code, lng(row, "total_debits"));
            creditByCode.put(code, lng(row, "total_credits"));
        }

        List<HierarchyNode> hierarchy = buildHierarchy(
                reportingRepository,
                tenantId,
                Set.of("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"),
                accountTypeByCode,
                accountNameByCode,
                debitByCode,
                creditByCode);

        List<TrialBalanceLineDto> lines = hierarchy.stream()
                .map(node -> TrialBalanceLineDto.builder()
                        .accountCode(node.code)
                        .accountName(node.name)
                        .accountType(node.type)
                        .parentAccountCode(node.parentCode)
                        .hierarchyLevel(node.level)
                        .leaf(node.children.isEmpty())
                        .debitBalance(node.ownPrimary)
                        .creditBalance(node.ownSecondary)
                        .rolledUpDebitBalance(node.rolledPrimary)
                        .rolledUpCreditBalance(node.rolledSecondary)
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

        Map<String, String> accountNameByCode = new HashMap<>();
        Map<String, String> accountTypeByCode = new HashMap<>();
        Map<String, Long> ownBalanceByCode = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String code = str(row, "account_code");
            String type = str(row, "account_type");
            long rawBalance = lng(row, "net_balance");
            boolean creditNormal = "LIABILITY".equals(type) || "EQUITY".equals(type);
            long balance = creditNormal ? -rawBalance : rawBalance;
            accountNameByCode.put(code, str(row, "account_name"));
            accountTypeByCode.put(code, type);
            ownBalanceByCode.put(code, balance);
        }

        List<HierarchyNode> hierarchy = buildHierarchy(
                reportingRepository,
                tenantId,
                Set.of("ASSET", "LIABILITY", "EQUITY"),
                accountTypeByCode,
                accountNameByCode,
                ownBalanceByCode,
                Map.of());

        List<BalanceSheetLineDto> assetLines = hierarchy.stream()
                .filter(node -> "ASSET".equals(node.type))
                .map(node -> BalanceSheetLineDto.builder()
                        .accountCode(node.code)
                        .accountName(node.name)
                        .parentAccountCode(node.parentCode)
                        .hierarchyLevel(node.level)
                        .leaf(node.children.isEmpty())
                        .balanceCents(node.ownPrimary)
                        .rolledUpBalanceCents(node.rolledPrimary)
                        .formattedBalance(formatCents(node.ownPrimary))
                        .formattedRolledUpBalance(formatCents(node.rolledPrimary))
                        .build())
                .toList();
        List<BalanceSheetLineDto> liabilityLines = hierarchy.stream()
                .filter(node -> "LIABILITY".equals(node.type))
                .map(node -> BalanceSheetLineDto.builder()
                        .accountCode(node.code)
                        .accountName(node.name)
                        .parentAccountCode(node.parentCode)
                        .hierarchyLevel(node.level)
                        .leaf(node.children.isEmpty())
                        .balanceCents(node.ownPrimary)
                        .rolledUpBalanceCents(node.rolledPrimary)
                        .formattedBalance(formatCents(node.ownPrimary))
                        .formattedRolledUpBalance(formatCents(node.rolledPrimary))
                        .build())
                .toList();
        List<BalanceSheetLineDto> equityLines = hierarchy.stream()
                .filter(node -> "EQUITY".equals(node.type))
                .map(node -> BalanceSheetLineDto.builder()
                        .accountCode(node.code)
                        .accountName(node.name)
                        .parentAccountCode(node.parentCode)
                        .hierarchyLevel(node.level)
                        .leaf(node.children.isEmpty())
                        .balanceCents(node.ownPrimary)
                        .rolledUpBalanceCents(node.rolledPrimary)
                        .formattedBalance(formatCents(node.ownPrimary))
                        .formattedRolledUpBalance(formatCents(node.rolledPrimary))
                        .build())
                .toList();

        long totalAssets = assetLines.stream().mapToLong(BalanceSheetLineDto::getBalanceCents).sum();
        long totalLiabilities = liabilityLines.stream().mapToLong(BalanceSheetLineDto::getBalanceCents).sum();
        long totalEquity = equityLines.stream().mapToLong(BalanceSheetLineDto::getBalanceCents).sum();
        long totalLiabilitiesAndEquity = totalLiabilities + totalEquity;

        return BalanceSheetReportDto.builder()
                .metadata(metadata("BALANCE_SHEET", tenantId, baseCurrency, asOfDate, null, null))
                .assets(BalanceSheetSectionDto.builder()
                        .sectionName("Assets")
                        .lines(assetLines)
                        .sectionTotal(totalAssets)
                        .build())
                .liabilities(BalanceSheetSectionDto.builder()
                        .sectionName("Liabilities")
                        .lines(liabilityLines)
                        .sectionTotal(totalLiabilities)
                        .build())
                .equity(BalanceSheetSectionDto.builder()
                        .sectionName("Equity")
                        .lines(equityLines)
                        .sectionTotal(totalEquity)
                        .build())
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

        Map<String, String> accountNameByCode = new HashMap<>();
        Map<String, String> accountTypeByCode = new HashMap<>();
        Map<String, Long> ownAmountByCode = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String code = str(row, "account_code");
            String type = str(row, "account_type");
            long amount = Math.abs(lng(row, "net_amount"));
            accountNameByCode.put(code, str(row, "account_name"));
            accountTypeByCode.put(code, type);
            ownAmountByCode.put(code, amount);
        }

        List<HierarchyNode> hierarchy = buildHierarchy(
                reportingRepository,
                tenantId,
                Set.of("REVENUE", "EXPENSE"),
                accountTypeByCode,
                accountNameByCode,
                ownAmountByCode,
                Map.of());

        List<IncomeStatementLineDto> revenueLines = hierarchy.stream()
                .filter(node -> "REVENUE".equals(node.type))
                .map(node -> IncomeStatementLineDto.builder()
                        .accountCode(node.code)
                        .accountName(node.name)
                        .parentAccountCode(node.parentCode)
                        .hierarchyLevel(node.level)
                        .leaf(node.children.isEmpty())
                        .amountCents(node.ownPrimary)
                        .rolledUpAmountCents(node.rolledPrimary)
                        .formattedAmount(formatCents(node.ownPrimary))
                        .formattedRolledUpAmount(formatCents(node.rolledPrimary))
                        .build())
                .toList();
        List<IncomeStatementLineDto> expenseLines = hierarchy.stream()
                .filter(node -> "EXPENSE".equals(node.type))
                .map(node -> IncomeStatementLineDto.builder()
                        .accountCode(node.code)
                        .accountName(node.name)
                        .parentAccountCode(node.parentCode)
                        .hierarchyLevel(node.level)
                        .leaf(node.children.isEmpty())
                        .amountCents(node.ownPrimary)
                        .rolledUpAmountCents(node.rolledPrimary)
                        .formattedAmount(formatCents(node.ownPrimary))
                        .formattedRolledUpAmount(formatCents(node.rolledPrimary))
                        .build())
                .toList();

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

    private static List<HierarchyNode> buildHierarchy(
            ReportingRepository reportingRepository,
            UUID tenantId,
            Set<String> allowedTypes,
            Map<String, String> typeByCode,
            Map<String, String> nameByCode,
            Map<String, Long> primaryByCode,
            Map<String, Long> secondaryByCode) {
        List<Map<String, Object>> hierarchyRows = reportingRepository.findAccountHierarchy(tenantId);
        if (hierarchyRows == null) {
            hierarchyRows = List.of();
        }
        Map<String, HierarchySeed> seedByCode = new LinkedHashMap<>();
        for (Map<String, Object> row : hierarchyRows) {
            String code = str(row, "account_code");
            String type = str(row, "account_type");
            if (!allowedTypes.contains(type)) {
                continue;
            }
            seedByCode.put(code, new HierarchySeed(
                    code,
                    str(row, "account_name"),
                    type,
                    nullableStr(row, "parent_account_code")));
        }

        for (Map.Entry<String, String> entry : typeByCode.entrySet()) {
            if (!allowedTypes.contains(entry.getValue())) {
                continue;
            }
            String code = entry.getKey();
            seedByCode.putIfAbsent(code, new HierarchySeed(
                    code,
                    nameByCode.getOrDefault(code, code),
                    entry.getValue(),
                    null));
        }

        Map<String, HierarchyNode> nodes = new LinkedHashMap<>();
        for (HierarchySeed seed : seedByCode.values()) {
            nodes.put(seed.code, new HierarchyNode(seed.code, seed.name, seed.type, seed.parentCode));
        }

        List<HierarchyNode> roots = new ArrayList<>();
        for (HierarchyNode node : nodes.values()) {
            node.ownPrimary = primaryByCode.getOrDefault(node.code, 0L);
            node.ownSecondary = secondaryByCode.getOrDefault(node.code, 0L);
            HierarchyNode parent = node.parentCode == null ? null : nodes.get(node.parentCode);
            if (parent == null || !node.type.equals(parent.type)) {
                roots.add(node);
                continue;
            }
            node.parent = parent;
            parent.children.add(node);
        }

        roots.sort((a, b) -> a.code.compareToIgnoreCase(b.code));
        for (HierarchyNode root : roots) {
            sortTree(root);
            assignLevel(root, 0);
            rollUp(root);
        }

        List<HierarchyNode> flattened = new ArrayList<>();
        for (HierarchyNode root : roots) {
            flatten(root, flattened);
        }
        return flattened;
    }

    private static void sortTree(HierarchyNode node) {
        node.children.sort((a, b) -> a.code.compareToIgnoreCase(b.code));
        for (HierarchyNode child : node.children) {
            sortTree(child);
        }
    }

    private static void assignLevel(HierarchyNode node, int level) {
        node.level = level;
        for (HierarchyNode child : node.children) {
            assignLevel(child, level + 1);
        }
    }

    private static void rollUp(HierarchyNode node) {
        long rolledPrimary = node.ownPrimary;
        long rolledSecondary = node.ownSecondary;
        for (HierarchyNode child : node.children) {
            rollUp(child);
            rolledPrimary += child.rolledPrimary;
            rolledSecondary += child.rolledSecondary;
        }
        node.rolledPrimary = rolledPrimary;
        node.rolledSecondary = rolledSecondary;
    }

    private static void flatten(HierarchyNode node, List<HierarchyNode> output) {
        output.add(node);
        for (HierarchyNode child : node.children) {
            flatten(child, output);
        }
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

    static String str(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val != null ? val.toString() : "";
    }

    static String nullableStr(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val == null ? null : val.toString();
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

    private record HierarchySeed(String code, String name, String type, String parentCode) {
    }

    private static final class HierarchyNode {
        private final String code;
        private final String name;
        private final String type;
        private final String parentCode;
        private HierarchyNode parent;
        private final List<HierarchyNode> children = new ArrayList<>();
        private int level;
        private long ownPrimary;
        private long ownSecondary;
        private long rolledPrimary;
        private long rolledSecondary;

        private HierarchyNode(String code, String name, String type, String parentCode) {
            this.code = code;
            this.name = name;
            this.type = type;
            this.parentCode = parentCode;
        }
    }
}
