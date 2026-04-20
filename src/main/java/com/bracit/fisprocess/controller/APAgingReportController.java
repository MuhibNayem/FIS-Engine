package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.Bill;
import com.bracit.fisprocess.domain.enums.BillStatus;
import com.bracit.fisprocess.dto.response.APAgingBucketDto;
import com.bracit.fisprocess.dto.response.APAgingReportDto;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for AP Aging reports.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ap/reports/aging")
@RequiredArgsConstructor
@Slf4j
@ApiVersion(1)
public class APAgingReportController {

    private final BillRepository billRepository;
    private final BusinessEntityRepository businessEntityRepository;

    /**
     * Generates an AP aging report as of a given date.
     *
     * @param tenantId the tenant UUID
     * @param asOfDate the date to calculate aging against
     * @param vendorId optional vendor filter
     * @return 200 OK with the aging report
     */
    @GetMapping
    public ResponseEntity<APAgingReportDto> getAgingReport(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @Nullable UUID vendorId) {
        APAgingReportDto report = buildAgingReport(tenantId, vendorId, asOfDate);
        return ResponseEntity.ok(report);
    }

    private APAgingReportDto buildAgingReport(UUID tenantId, @Nullable UUID vendorId, LocalDate asOfDate) {
        // Validate tenant
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        // Find all posted/partially paid bills with outstanding balance
        List<BillStatus> activeStatuses = Arrays.asList(
                BillStatus.POSTED,
                BillStatus.PARTIALLY_PAID,
                BillStatus.OVERDUE);

        List<Bill> bills;
        if (vendorId != null) {
            bills = billRepository.findByTenantIdAndVendorIdAndStatusIn(tenantId, vendorId, activeStatuses);
        } else {
            bills = fetchAllActiveBills(tenantId);
        }

        // Build aging buckets
        AgingBuckets buckets = new AgingBuckets();

        for (Bill bill : bills) {
            Long outstanding = bill.getOutstandingAmount();
            if (outstanding == null || outstanding <= 0) {
                continue;
            }

            long daysOverdue = ChronoUnit.DAYS.between(bill.getDueDate(), asOfDate);
            if (daysOverdue <= 0) {
                buckets.currentCount++;
                buckets.currentAmount += outstanding;
            } else if (daysOverdue <= 30) {
                buckets.bucket0to30Count++;
                buckets.bucket0to30Amount += outstanding;
            } else if (daysOverdue <= 60) {
                buckets.bucket31to60Count++;
                buckets.bucket31to60Amount += outstanding;
            } else if (daysOverdue <= 90) {
                buckets.bucket61to90Count++;
                buckets.bucket61to90Amount += outstanding;
            } else {
                buckets.bucket90PlusCount++;
                buckets.bucket90PlusAmount += outstanding;
            }
        }

        List<APAgingBucketDto> bucketDtos = new ArrayList<>();
        if (buckets.bucket0to30Amount > 0 || buckets.bucket0to30Count > 0) {
            bucketDtos.add(new APAgingBucketDto("0-30", buckets.bucket0to30Count, buckets.bucket0to30Amount));
        }
        if (buckets.bucket31to60Amount > 0 || buckets.bucket31to60Count > 0) {
            bucketDtos.add(new APAgingBucketDto("31-60", buckets.bucket31to60Count, buckets.bucket31to60Amount));
        }
        if (buckets.bucket61to90Amount > 0 || buckets.bucket61to90Count > 0) {
            bucketDtos.add(new APAgingBucketDto("61-90", buckets.bucket61to90Count, buckets.bucket61to90Amount));
        }
        if (buckets.bucket90PlusAmount > 0 || buckets.bucket90PlusCount > 0) {
            bucketDtos.add(new APAgingBucketDto("90+", buckets.bucket90PlusCount, buckets.bucket90PlusAmount));
        }

        String currency = bills.isEmpty() ? "USD" : bills.get(0).getCurrency();
        long totalOutstanding = buckets.getTotal();

        return APAgingReportDto.builder()
                .asOfDate(asOfDate.toString())
                .vendorId(vendorId != null ? vendorId.toString() : null)
                .currency(currency)
                .buckets(bucketDtos)
                .totalOutstanding(totalOutstanding)
                .build();
    }

    private List<Bill> fetchAllActiveBills(UUID tenantId) {
        List<Bill> allBills = new ArrayList<>();
        int page = 0;
        int pageSize = 1000;
        while (true) {
            var pageResult = billRepository.findByTenantIdWithFilters(
                    tenantId, null, null,
                    org.springframework.data.domain.PageRequest.of(page, pageSize));
            allBills.addAll(pageResult.getContent());
            if (pageResult.isLast()) {
                break;
            }
            page++;
        }
        return allBills;
    }

    private static final class AgingBuckets {
        int bucket0to30Count;
        long bucket0to30Amount;
        int bucket31to60Count;
        long bucket31to60Amount;
        int bucket61to90Count;
        long bucket61to90Amount;
        int bucket90PlusCount;
        long bucket90PlusAmount;
        int currentCount;
        long currentAmount;

        long getTotal() {
            return bucket0to30Amount + bucket31to60Amount + bucket61to90Amount + bucket90PlusAmount;
        }
    }
}
