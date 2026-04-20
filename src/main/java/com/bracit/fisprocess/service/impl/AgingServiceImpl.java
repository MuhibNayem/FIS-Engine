package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.dto.response.ARAgingBucketDto;
import com.bracit.fisprocess.dto.response.ARAgingReportDto;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.InvoiceRepository;
import com.bracit.fisprocess.service.AgingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link AgingService} for AR Aging reports.
 * <p>
 * Groups outstanding invoice balances into aging buckets:
 * 0-30, 31-60, 61-90, 90+ days overdue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgingServiceImpl implements AgingService {

    private final InvoiceRepository invoiceRepository;
    private final BusinessEntityRepository businessEntityRepository;

    @Override
    public ARAgingReportDto getAgingReport(
            UUID tenantId,
            @Nullable UUID customerId,
            LocalDate asOfDate) {
        validateTenantExists(tenantId);

        // Find all posted/partially paid invoices with outstanding balance
        List<InvoiceStatus> activeStatuses = Arrays.asList(
                InvoiceStatus.POSTED,
                InvoiceStatus.PARTIALLY_PAID,
                InvoiceStatus.OVERDUE);

        List<Invoice> invoices;
        if (customerId != null) {
            invoices = invoiceRepository.findByTenantIdAndCustomerIdAndStatusIn(
                    tenantId, customerId, activeStatuses);
        } else {
            // Fetch all invoices for tenant — filter in memory for flexibility
            invoices = invoiceRepository.findByTenantIdAndCustomerIdAndStatusIn(
                    tenantId, null, activeStatuses);
            // Since null customerId won't match, we need a broader query
            // Use the paginated query and collect all
            invoices = fetchAllActiveInvoices(tenantId);
        }

        // Build aging buckets
        AgingBuckets buckets = new AgingBuckets();

        for (Invoice invoice : invoices) {
            Long outstanding = invoice.getOutstandingAmount();
            if (outstanding == null || outstanding <= 0) {
                continue;
            }

            long daysOverdue = ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate);
            if (daysOverdue <= 0) {
                // Not yet due — bucket as "current"
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

        List<ARAgingBucketDto> bucketDtos = new ArrayList<>();
        if (buckets.bucket0to30Amount > 0 || buckets.bucket0to30Count > 0) {
            bucketDtos.add(new ARAgingBucketDto("0-30", buckets.bucket0to30Count, buckets.bucket0to30Amount));
        }
        if (buckets.bucket31to60Amount > 0 || buckets.bucket31to60Count > 0) {
            bucketDtos.add(new ARAgingBucketDto("31-60", buckets.bucket31to60Count, buckets.bucket31to60Amount));
        }
        if (buckets.bucket61to90Amount > 0 || buckets.bucket61to90Count > 0) {
            bucketDtos.add(new ARAgingBucketDto("61-90", buckets.bucket61to90Count, buckets.bucket61to90Amount));
        }
        if (buckets.bucket90PlusAmount > 0 || buckets.bucket90PlusCount > 0) {
            bucketDtos.add(new ARAgingBucketDto("90+", buckets.bucket90PlusCount, buckets.bucket90PlusAmount));
        }

        // Determine currency
        String currency = invoices.isEmpty() ? "USD" : invoices.get(0).getCurrency();

        long totalOutstanding = buckets.getTotal();

        return ARAgingReportDto.builder()
                .asOfDate(asOfDate.toString())
                .customerId(customerId != null ? customerId.toString() : null)
                .currency(currency)
                .buckets(bucketDtos)
                .totalOutstanding(totalOutstanding)
                .build();
    }

    // --- Private Helper Methods ---

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private List<Invoice> fetchAllActiveInvoices(UUID tenantId) {
        List<Invoice> allInvoices = new ArrayList<>();
        int page = 0;
        int pageSize = 1000;
        while (true) {
            var pageResult = invoiceRepository.findByTenantIdWithFilters(
                    tenantId, null, null,
                    org.springframework.data.domain.PageRequest.of(page, pageSize));
            allInvoices.addAll(pageResult.getContent());
            if (pageResult.isLast()) {
                break;
            }
            page++;
        }
        return allInvoices;
    }

    /**
     * Accumulator for aging bucket counts and amounts.
     */
    private static final class AgingBuckets {
        // 0-30 days
        int bucket0to30Count;
        long bucket0to30Amount;
        // 31-60 days
        int bucket31to60Count;
        long bucket31to60Amount;
        // 61-90 days
        int bucket61to90Count;
        long bucket61to90Amount;
        // 90+ days
        int bucket90PlusCount;
        long bucket90PlusAmount;
        // Current (not yet due)
        int currentCount;
        long currentAmount;

        long getTotal() {
            return bucket0to30Amount + bucket31to60Amount + bucket61to90Amount + bucket90PlusAmount;
        }
    }
}
