package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.dto.response.AgingBucketDto;
import com.bracit.fisprocess.dto.response.AgingReportDto;
import com.bracit.fisprocess.dto.response.CashFlowLineDto;
import com.bracit.fisprocess.dto.response.CashFlowReportDto;
import com.bracit.fisprocess.dto.response.CashFlowSectionDto;
import com.bracit.fisprocess.dto.response.FxExposureLineDto;
import com.bracit.fisprocess.dto.response.FxExposureReportDto;
import com.bracit.fisprocess.exception.ReportParameterException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.ReportingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ReportingRiskOps {

    private ReportingRiskOps() {
    }

    static CashFlowReportDto generateCashFlow(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate) {
        ReportingStatementOps.validateDateRange(fromDate, toDate);
        String baseCurrency = ReportingStatementOps.resolveBaseCurrency(businessEntityRepository, tenantId);

        List<Map<String, Object>> movements = reportingRepository.findNetMovementByAccountType(tenantId, fromDate, toDate);
        long openingCash = reportingRepository.findCashBalance(tenantId, fromDate.minusDays(1));

        List<CashFlowLineDto> operatingLines = new ArrayList<>();
        List<CashFlowLineDto> investingLines = new ArrayList<>();
        List<CashFlowLineDto> financingLines = new ArrayList<>();

        for (Map<String, Object> row : movements) {
            String type = ReportingStatementOps.str(row, "account_type");
            long netMovement = ReportingStatementOps.lng(row, "net_movement");
            String label = ReportingStatementOps.str(row, "account_name")
                    + " (" + ReportingStatementOps.str(row, "account_code") + ")";

            CashFlowLineDto line = CashFlowLineDto.builder()
                    .label(label)
                    .amountCents(netMovement)
                    .build();

            switch (type) {
                case "REVENUE", "EXPENSE" -> operatingLines.add(line);
                case "ASSET" -> investingLines.add(line);
                case "LIABILITY", "EQUITY" -> financingLines.add(line);
                default -> {
                    // no-op for unknown type
                }
            }
        }

        CashFlowSectionDto operating = CashFlowSectionDto.builder()
                .sectionName("Operating Activities")
                .lines(operatingLines)
                .sectionTotal(operatingLines.stream().mapToLong(CashFlowLineDto::getAmountCents).sum())
                .build();

        CashFlowSectionDto investing = CashFlowSectionDto.builder()
                .sectionName("Investing Activities")
                .lines(investingLines)
                .sectionTotal(investingLines.stream().mapToLong(CashFlowLineDto::getAmountCents).sum())
                .build();

        CashFlowSectionDto financing = CashFlowSectionDto.builder()
                .sectionName("Financing Activities")
                .lines(financingLines)
                .sectionTotal(financingLines.stream().mapToLong(CashFlowLineDto::getAmountCents).sum())
                .build();

        long netCashChange = operating.getSectionTotal() + investing.getSectionTotal() + financing.getSectionTotal();

        return CashFlowReportDto.builder()
                .metadata(ReportingStatementOps.metadata("CASH_FLOW", tenantId, baseCurrency, null, fromDate, toDate))
                .operatingActivities(operating)
                .investingActivities(investing)
                .financingActivities(financing)
                .netCashChange(netCashChange)
                .openingCash(openingCash)
                .closingCash(openingCash + netCashChange)
                .build();
    }

    static FxExposureReportDto generateFxExposure(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            LocalDate asOfDate) {
        String baseCurrency = ReportingStatementOps.resolveBaseCurrency(businessEntityRepository, tenantId);
        List<Map<String, Object>> rows = reportingRepository.findFxExposure(tenantId, asOfDate);

        Map<String, long[]> exposureMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String currency = ReportingStatementOps.str(row, "currency");
            String type = ReportingStatementOps.str(row, "account_type");
            long balance = ReportingStatementOps.lng(row, "net_balance");

            long[] exposure = exposureMap.computeIfAbsent(currency, ignored -> new long[2]);
            if ("ASSET".equals(type)) {
                exposure[0] += balance;
            } else {
                exposure[1] += balance;
            }
        }

        long totalUnrealized = 0L;
        List<FxExposureLineDto> exposures = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : exposureMap.entrySet()) {
            String currency = entry.getKey();
            long assetExposure = entry.getValue()[0];
            long liabilityExposure = entry.getValue()[1];
            long netExposure = assetExposure + liabilityExposure;

            BigDecimal rate = reportingRepository.findLatestRate(tenantId, currency, baseCurrency);
            long unrealized = rate.compareTo(BigDecimal.ONE) != 0
                    ? netExposure - BigDecimal.valueOf(netExposure).divide(rate, 0, java.math.RoundingMode.HALF_UP).longValue()
                    : 0L;
            totalUnrealized += unrealized;

            exposures.add(FxExposureLineDto.builder()
                    .currency(currency)
                    .assetExposure(assetExposure)
                    .liabilityExposure(liabilityExposure)
                    .netExposure(netExposure)
                    .currentRate(rate)
                    .unrealizedGainLoss(unrealized)
                    .build());
        }

        return FxExposureReportDto.builder()
                .metadata(ReportingStatementOps.metadata("FX_EXPOSURE", tenantId, baseCurrency, asOfDate, null, null))
                .baseCurrency(baseCurrency)
                .exposures(exposures)
                .totalUnrealizedGainLoss(totalUnrealized)
                .build();
    }

    static AgingReportDto generateAging(
            ReportingRepository reportingRepository,
            BusinessEntityRepository businessEntityRepository,
            UUID tenantId,
            String accountType,
            LocalDate asOfDate) {
        if (accountType == null || accountType.isBlank()) {
            throw new ReportParameterException("accountType is required (ASSET or LIABILITY).");
        }
        if (!"ASSET".equals(accountType) && !"LIABILITY".equals(accountType)) {
            throw new ReportParameterException("accountType must be ASSET or LIABILITY for aging analysis.");
        }

        String baseCurrency = ReportingStatementOps.resolveBaseCurrency(businessEntityRepository, tenantId);
        List<Map<String, Object>> rows = reportingRepository.findAgingBuckets(tenantId, accountType, asOfDate);

        List<AgingBucketDto> buckets = rows.stream()
                .map(row -> AgingBucketDto.builder()
                        .bucketLabel(ReportingStatementOps.str(row, "bucket_label"))
                        .amountCents(ReportingStatementOps.lng(row, "amount_cents"))
                        .entryCount(ReportingStatementOps.lng(row, "entry_count"))
                        .build())
                .toList();

        long grandTotal = buckets.stream().mapToLong(AgingBucketDto::getAmountCents).sum();

        return AgingReportDto.builder()
                .metadata(ReportingStatementOps.metadata("AGING_ANALYSIS", tenantId, baseCurrency, asOfDate, null, null))
                .accountType(accountType)
                .buckets(buckets)
                .grandTotal(grandTotal)
                .build();
    }
}
