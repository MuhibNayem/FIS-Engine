package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.dto.response.ARAgingBucketDto;
import com.bracit.fisprocess.dto.response.ARAgingReportDto;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgingServiceImpl Unit Tests")
class AgingServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;

    @InjectMocks
    private AgingServiceImpl agingService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private BusinessEntity activeTenant;

    @BeforeEach
    void setUp() {
        activeTenant = BusinessEntity.builder()
                .tenantId(TENANT_ID)
                .name("Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // --- getAgingReport Tests ---

    @Nested
    @DisplayName("getAgingReport")
    class GetAgingReportTests {

        @Test
        @DisplayName("should correctly bucket invoices by days overdue")
        void shouldCorrectlyBucketInvoices() {
            LocalDate asOfDate = LocalDate.of(2026, 4, 13);

            // Invoice 1: due 30 days ago → 0-30 bucket, outstanding = total - paid = 5000 - 1000
            Invoice inv1 = buildInvoiceWithPaid(5000L, 1000L, asOfDate.minusDays(30), 1000L);
            // Invoice 2: due 45 days ago → 31-60 bucket
            Invoice inv2 = buildInvoiceWithPaid(8000L, 1200L, asOfDate.minusDays(45), 1200L);
            // Invoice 3: due 75 days ago → 61-90 bucket
            Invoice inv3 = buildInvoiceWithPaid(3000L, 450L, asOfDate.minusDays(75), 450L);
            // Invoice 4: due 120 days ago → 90+ bucket
            Invoice inv4 = buildInvoiceWithPaid(10000L, 1500L, asOfDate.minusDays(120), 1500L);
            // Invoice 5: not yet due (due in future) → current
            Invoice inv5 = buildInvoiceWithPaid(2000L, 300L, asOfDate.plusDays(10), 300L);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(invoiceRepository.findByTenantIdAndCustomerIdAndStatusIn(
                    eq(TENANT_ID), any(), any()))
                    .thenReturn(List.of(inv1, inv2, inv3, inv4, inv5));

            ARAgingReportDto report = agingService.getAgingReport(TENANT_ID, CUSTOMER_ID, asOfDate);

            assertThat(report).isNotNull();
            assertThat(report.getBuckets()).hasSize(4); // 0-30, 31-60, 61-90, 90+

            ARAgingBucketDto bucket0to30 = report.getBuckets().stream()
                    .filter(b -> b.getBucket().equals("0-30"))
                    .findFirst().orElseThrow();
            assertThat(bucket0to30.getTotalAmount()).isEqualTo(4000L); // 5000 - 1000 paid

            ARAgingBucketDto bucket31to60 = report.getBuckets().stream()
                    .filter(b -> b.getBucket().equals("31-60"))
                    .findFirst().orElseThrow();
            assertThat(bucket31to60.getTotalAmount()).isEqualTo(6800L); // 8000 - 1200 paid
        }

        @Test
        @DisplayName("should filter by customer when customerId is provided")
        void shouldFilterByCustomer() {
            LocalDate asOfDate = LocalDate.of(2026, 4, 13);
            Invoice inv = buildInvoice(5000L, 1000L, asOfDate.minusDays(10));

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(invoiceRepository.findByTenantIdAndCustomerIdAndStatusIn(
                    eq(TENANT_ID), eq(CUSTOMER_ID), any()))
                    .thenReturn(List.of(inv));

            ARAgingReportDto report = agingService.getAgingReport(TENANT_ID, CUSTOMER_ID, asOfDate);

            assertThat(report).isNotNull();
            assertThat(report.getCustomerId()).isEqualTo(CUSTOMER_ID.toString());
        }

        @Test
        @DisplayName("should return empty report when no outstanding invoices")
        void shouldReturnEmptyWhenNoOutstanding() {
            LocalDate asOfDate = LocalDate.of(2026, 4, 13);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(invoiceRepository.findByTenantIdAndCustomerIdAndStatusIn(
                    eq(TENANT_ID), any(), any()))
                    .thenReturn(List.of());
            when(invoiceRepository.findByTenantIdWithFilters(eq(TENANT_ID), any(), any(), any()))
                    .thenReturn(Page.empty(PageRequest.of(0, 1000)));

            ARAgingReportDto report = agingService.getAgingReport(TENANT_ID, null, asOfDate);

            assertThat(report).isNotNull();
            assertThat(report.getBuckets()).isEmpty();
            assertThat(report.getTotalOutstanding()).isZero();
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> agingService.getAgingReport(
                    TENANT_ID, null, LocalDate.now()))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should calculate as-of-date correctly for overdue invoices")
        void shouldCalculateAsOfDateCorrectly() {
            LocalDate asOfDate = LocalDate.of(2026, 4, 13);
            // Invoice due 365 days ago — well into 90+ bucket, outstanding = total - paid
            Invoice inv = buildInvoiceWithPaid(100000L, 15000L, asOfDate.minusDays(365), 15000L);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(invoiceRepository.findByTenantIdAndCustomerIdAndStatusIn(
                    eq(TENANT_ID), any(), any()))
                    .thenReturn(List.of(inv));

            ARAgingReportDto report = agingService.getAgingReport(TENANT_ID, CUSTOMER_ID, asOfDate);

            assertThat(report.getBuckets()).hasSize(1);
            assertThat(report.getBuckets().get(0).getBucket()).isEqualTo("90+");
            assertThat(report.getBuckets().get(0).getTotalAmount()).isEqualTo(85000L);
        }
    }

    // --- Helpers ---

    private Invoice buildInvoice(long total, long tax, LocalDate dueDate) {
        return buildInvoiceWithPaid(total, tax, dueDate, 0L);
    }

    private Invoice buildInvoiceWithPaid(long total, long tax, LocalDate dueDate, long paidAmount) {
        return Invoice.builder()
                .invoiceId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .customerId(CUSTOMER_ID)
                .invoiceNumber("INV-AGING-TEST")
                .issueDate(dueDate.minusDays(30))
                .dueDate(dueDate)
                .currency("USD")
                .subtotalAmount(total - tax)
                .taxAmount(tax)
                .totalAmount(total)
                .paidAmount(paidAmount)
                .status(InvoiceStatus.POSTED)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
