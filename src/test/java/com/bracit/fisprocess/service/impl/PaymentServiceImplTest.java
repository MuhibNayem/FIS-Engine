package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.ARPayment;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.entity.PaymentApplication;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.domain.enums.PaymentMethod;
import com.bracit.fisprocess.domain.enums.PaymentStatus;
import com.bracit.fisprocess.dto.request.ApplyPaymentRequestDto;
import com.bracit.fisprocess.dto.request.ApplyPaymentRequestDto.PaymentApplicationRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.RecordPaymentRequestDto;
import com.bracit.fisprocess.dto.response.PaymentResponseDto;
import com.bracit.fisprocess.exception.InvalidInvoiceException;
import com.bracit.fisprocess.exception.PaymentExceedsOutstandingException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.ARPaymentRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.CustomerRepository;
import com.bracit.fisprocess.repository.InvoiceRepository;
import com.bracit.fisprocess.repository.PaymentApplicationRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PeriodValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentServiceImpl Unit Tests")
class PaymentServiceImplTest {

    @Mock
    private ARPaymentRepository arPaymentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentApplicationRepository paymentApplicationRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private JournalEntryService journalEntryService;
    @Mock
    private PeriodValidationService periodValidationService;
    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();

    private BusinessEntity activeTenant;

    @BeforeEach
    void setUp() {
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true);

        activeTenant = BusinessEntity.builder()
                .tenantId(TENANT_ID)
                .name("Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        // Simulate default method delegation: 2-arg → 4-arg for JournalEntryService
        // Use lenient() since not all tests call applyPayment
        doAnswer(invocation -> {
            UUID tid = invocation.getArgument(0);
            Object req = invocation.getArgument(1);
            return journalEntryService.createJournalEntry(tid, (CreateJournalEntryRequestDto) req, null, null);
        }).when(journalEntryService).createJournalEntry(any(UUID.class), any());
        lenient().when(journalEntryService.createJournalEntry(any(UUID.class), any(), any(), any()))
                .thenReturn(null);
    }

    // --- recordPayment Tests ---

    @Nested
    @DisplayName("recordPayment")
    class RecordPaymentTests {

        @Test
        @DisplayName("should record payment successfully")
        void shouldRecordPaymentSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.findById(CUSTOMER_ID))
                    .thenReturn(Optional.of(Customer.builder()
                            .customerId(CUSTOMER_ID)
                            .tenantId(TENANT_ID)
                            .build()));

            ARPayment saved = buildPayment(PaymentStatus.PENDING);
            when(arPaymentRepository.save(any(ARPayment.class))).thenReturn(saved);

            RecordPaymentRequestDto request = RecordPaymentRequestDto.builder()
                    .customerId(CUSTOMER_ID)
                    .amount(5000L)
                    .paymentDate(LocalDate.now())
                    .method(PaymentMethod.BANK_TRANSFER)
                    .reference("REF-001")
                    .build();

            ARPayment result = paymentService.recordPayment(TENANT_ID, request, "admin");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.getAmount()).isEqualTo(5000L);
            verify(arPaymentRepository).save(any(ARPayment.class));
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            RecordPaymentRequestDto request = RecordPaymentRequestDto.builder()
                    .customerId(CUSTOMER_ID)
                    .amount(5000L)
                    .paymentDate(LocalDate.now())
                    .method(PaymentMethod.BANK_TRANSFER)
                    .build();

            assertThatThrownBy(() -> paymentService.recordPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(TenantNotFoundException.class);
        }
    }

    // --- applyPayment Tests ---

    @Nested
    @DisplayName("applyPayment")
    class ApplyPaymentTests {

        @Test
        @DisplayName("should apply payment to invoice successfully")
        void shouldApplyPaymentSuccessfully() {
            ARPayment payment = buildPayment(PaymentStatus.PENDING);
            Invoice invoice = buildInvoice(10000L, 2000L); // outstanding = 8000

            when(arPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(invoice));
            when(arPaymentRepository.save(any(ARPayment.class))).thenReturn(payment);
            when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);

            ApplyPaymentRequestDto request = ApplyPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(PaymentApplicationRequestDto.builder()
                            .invoiceId(INVOICE_ID)
                            .appliedAmount(5000L)
                            .build()))
                    .build();

            ARPayment result = paymentService.applyPayment(TENANT_ID, request, "admin");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPLIED);
            verify(journalEntryService).createJournalEntry(any(), any(), any(), any());
            assertThat(invoice.getPaidAmount()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("should reject when applied amount exceeds outstanding")
        void shouldRejectWhenExceedsOutstanding() {
            ARPayment payment = buildPayment(PaymentStatus.PENDING);
            Invoice invoice = buildInvoice(10000L, 2000L); // outstanding = 8000

            when(arPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(invoice));

            ApplyPaymentRequestDto request = ApplyPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(PaymentApplicationRequestDto.builder()
                            .invoiceId(INVOICE_ID)
                            .appliedAmount(15000L) // exceeds outstanding
                            .build()))
                    .build();

            assertThatThrownBy(() -> paymentService.applyPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(PaymentExceedsOutstandingException.class);
            verify(journalEntryService, never()).createJournalEntry(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should reject when total applied exceeds payment amount")
        void shouldRejectWhenTotalAppliedExceedsPaymentAmount() {
            ARPayment payment = buildPayment(PaymentStatus.PENDING);
            // Payment amount = 5000, but we try to apply 6000
            Invoice invoice = buildInvoice(20000L, 3000L); // outstanding = 17000

            when(arPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(invoice));

            ApplyPaymentRequestDto request = ApplyPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(PaymentApplicationRequestDto.builder()
                            .invoiceId(INVOICE_ID)
                            .appliedAmount(6000L) // exceeds payment amount of 5000
                            .build()))
                    .build();

            assertThatThrownBy(() -> paymentService.applyPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(PaymentExceedsOutstandingException.class)
                    .hasMessageContaining("exceeds payment amount");
        }

        @Test
        @DisplayName("should reject when invoice is not POSTED or PARTIALLY_PAID")
        void shouldRejectWhenInvoiceNotPosted() {
            ARPayment payment = buildPayment(PaymentStatus.PENDING);
            Invoice draftInvoice = buildInvoice(10000L, 2000L);
            draftInvoice.setStatus(InvoiceStatus.DRAFT);

            when(arPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(draftInvoice));

            ApplyPaymentRequestDto request = ApplyPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(PaymentApplicationRequestDto.builder()
                            .invoiceId(INVOICE_ID)
                            .appliedAmount(5000L)
                            .build()))
                    .build();

            assertThatThrownBy(() -> paymentService.applyPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(InvalidInvoiceException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("should handle partial payment correctly")
        void shouldHandlePartialPayment() {
            ARPayment payment = buildPayment(PaymentStatus.PENDING);
            Invoice invoice = buildInvoice(10000L, 0L); // outstanding = 10000

            when(arPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(invoice));
            when(arPaymentRepository.save(any(ARPayment.class))).thenReturn(payment);
            when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);

            ApplyPaymentRequestDto request = ApplyPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(PaymentApplicationRequestDto.builder()
                            .invoiceId(INVOICE_ID)
                            .appliedAmount(3000L) // partial — less than outstanding
                            .build()))
                    .build();

            ARPayment result = paymentService.applyPayment(TENANT_ID, request, "admin");

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
            assertThat(invoice.getOutstandingAmount()).isEqualTo(7000L);
        }
    }

    // --- listPayments Tests ---

    @Nested
    @DisplayName("listPayments")
    class ListPaymentsTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ARPayment> page = new PageImpl<>(List.of(buildPayment(PaymentStatus.PENDING)), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(arPaymentRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<PaymentResponseDto> result = paymentService.listPayments(TENANT_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // --- Helpers ---

    private ARPayment buildPayment(PaymentStatus status) {
        return ARPayment.builder()
                .paymentId(PAYMENT_ID)
                .tenantId(TENANT_ID)
                .customerId(CUSTOMER_ID)
                .amount(5000L)
                .paymentDate(LocalDate.now())
                .method(PaymentMethod.BANK_TRANSFER)
                .status(status)
                .applications(new ArrayList<>())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private Invoice buildInvoice(long total, long tax) {
        return Invoice.builder()
                .invoiceId(INVOICE_ID)
                .tenantId(TENANT_ID)
                .customerId(CUSTOMER_ID)
                .invoiceNumber("INV-TEST-001")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .currency("USD")
                .subtotalAmount(total - tax)
                .taxAmount(tax)
                .totalAmount(total)
                .paidAmount(0L)
                .status(InvoiceStatus.POSTED)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
