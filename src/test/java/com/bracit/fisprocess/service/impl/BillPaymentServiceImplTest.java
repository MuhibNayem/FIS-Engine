package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Bill;
import com.bracit.fisprocess.domain.entity.BillLine;
import com.bracit.fisprocess.domain.entity.BillPayment;
import com.bracit.fisprocess.domain.entity.BillPaymentApplication;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Vendor;
import com.bracit.fisprocess.domain.enums.BillPaymentStatus;
import com.bracit.fisprocess.domain.enums.BillStatus;
import com.bracit.fisprocess.domain.enums.PaymentMethod;
import com.bracit.fisprocess.domain.enums.PaymentTerms;
import com.bracit.fisprocess.dto.request.ApplyBillPaymentRequestDto;
import com.bracit.fisprocess.dto.request.ApplyBillPaymentRequestDto.BillPaymentApplicationRequestDto;
import com.bracit.fisprocess.dto.request.RecordBillPaymentRequestDto;
import com.bracit.fisprocess.dto.response.BillPaymentResponseDto;
import com.bracit.fisprocess.exception.BillNotFoundException;
import com.bracit.fisprocess.exception.BillPaymentExceedsOutstandingException;
import com.bracit.fisprocess.exception.InvalidBillException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.exception.VendorNotFoundException;
import com.bracit.fisprocess.repository.BillPaymentApplicationRepository;
import com.bracit.fisprocess.repository.BillPaymentRepository;
import com.bracit.fisprocess.repository.BillRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.VendorRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BillPaymentServiceImpl Unit Tests")
class BillPaymentServiceImplTest {

    @Mock
    private BillPaymentRepository billPaymentRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private BillPaymentApplicationRepository billPaymentApplicationRepository;
    @Mock
    private VendorRepository vendorRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private JournalEntryService journalEntryService;
    @Mock
    private PeriodValidationService periodValidationService;
    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private BillPaymentServiceImpl billPaymentService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID VENDOR_ID = UUID.randomUUID();
    private static final UUID BILL_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    private BusinessEntity activeTenant;
    private Vendor activeVendor;
    private Bill postedBill;
    private BillPayment pendingPayment;

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
                .build();

        activeVendor = Vendor.builder()
                .vendorId(VENDOR_ID)
                .tenantId(TENANT_ID)
                .code("VEND-001")
                .name("Test Vendor")
                .status(Vendor.VendorStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();

        postedBill = Bill.builder()
                .billId(BILL_ID)
                .tenantId(TENANT_ID)
                .vendorId(VENDOR_ID)
                .billNumber("BILL-001")
                .billDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .currency("USD")
                .subtotalAmount(10000L)
                .taxAmount(1500L)
                .totalAmount(11500L)
                .status(BillStatus.POSTED)
                .paidAmount(0L)
                .createdAt(OffsetDateTime.now())
                .build();

        pendingPayment = BillPayment.builder()
                .billPaymentId(PAYMENT_ID)
                .tenantId(TENANT_ID)
                .vendorId(VENDOR_ID)
                .amount(11500L)
                .paymentDate(LocalDate.now())
                .method(PaymentMethod.BANK_TRANSFER)
                .status(BillPaymentStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
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
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(activeVendor));

            BillPayment saved = BillPayment.builder()
                    .billPaymentId(PAYMENT_ID)
                    .tenantId(TENANT_ID)
                    .vendorId(VENDOR_ID)
                    .amount(11500L)
                    .paymentDate(LocalDate.now())
                    .method(PaymentMethod.BANK_TRANSFER)
                    .status(BillPaymentStatus.PENDING)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(billPaymentRepository.save(any(BillPayment.class))).thenReturn(saved);

            RecordBillPaymentRequestDto request = RecordBillPaymentRequestDto.builder()
                    .vendorId(VENDOR_ID)
                    .amount(11500L)
                    .paymentDate(LocalDate.now())
                    .method(PaymentMethod.BANK_TRANSFER)
                    .build();

            BillPayment result = billPaymentService.recordPayment(TENANT_ID, request, "admin");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(BillPaymentStatus.PENDING);
            verify(billPaymentRepository).save(any(BillPayment.class));
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            RecordBillPaymentRequestDto request = RecordBillPaymentRequestDto.builder()
                    .vendorId(VENDOR_ID)
                    .amount(1000L)
                    .paymentDate(LocalDate.now())
                    .method(PaymentMethod.CASH)
                    .build();

            assertThatThrownBy(() -> billPaymentService.recordPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when vendor not found")
        void shouldThrowWhenVendorNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.empty());

            RecordBillPaymentRequestDto request = RecordBillPaymentRequestDto.builder()
                    .vendorId(VENDOR_ID)
                    .amount(1000L)
                    .paymentDate(LocalDate.now())
                    .method(PaymentMethod.CASH)
                    .build();

            assertThatThrownBy(() -> billPaymentService.recordPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(VendorNotFoundException.class);
        }
    }

    // --- applyPayment Tests ---

    @Nested
    @DisplayName("applyPayment")
    class ApplyPaymentTests {

        @Test
        @DisplayName("should apply payment to bill successfully")
        void shouldApplyPaymentSuccessfully() {
            when(billPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(pendingPayment));
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(postedBill));
            when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));
            doAnswer(invocation -> null)
                    .when(journalEntryService).createJournalEntry(any(UUID.class), any());

            ApplyBillPaymentRequestDto request = ApplyBillPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(BillPaymentApplicationRequestDto.builder()
                            .billId(BILL_ID)
                            .appliedAmount(11500L)
                            .build()))
                    .build();

            BillPayment result = billPaymentService.applyPayment(TENANT_ID, request, "admin");

            assertThat(result.getStatus()).isEqualTo(BillPaymentStatus.APPLIED);
            verify(journalEntryService).createJournalEntry(any(UUID.class), any());
        }

        @Test
        @DisplayName("should throw when payment not in PENDING status")
        void shouldThrowWhenPaymentNotPending() {
            BillPayment appliedPayment = BillPayment.builder()
                    .billPaymentId(PAYMENT_ID)
                    .tenantId(TENANT_ID)
                    .vendorId(VENDOR_ID)
                    .amount(11500L)
                    .status(BillPaymentStatus.APPLIED)
                    .build();
            when(billPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(appliedPayment));

            ApplyBillPaymentRequestDto request = ApplyBillPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(BillPaymentApplicationRequestDto.builder()
                            .billId(BILL_ID)
                            .appliedAmount(11500L)
                            .build()))
                    .build();

            assertThatThrownBy(() -> billPaymentService.applyPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(InvalidBillException.class)
                    .hasMessageContaining("PENDING");
        }

        @Test
        @DisplayName("should throw when bill not found")
        void shouldThrowWhenBillNotFound() {
            when(billPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(pendingPayment));
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.empty());

            ApplyBillPaymentRequestDto request = ApplyBillPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(BillPaymentApplicationRequestDto.builder()
                            .billId(BILL_ID)
                            .appliedAmount(11500L)
                            .build()))
                    .build();

            assertThatThrownBy(() -> billPaymentService.applyPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(BillNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when bill is not POSTED")
        void shouldThrowWhenBillNotPosted() {
            Bill draftBill = Bill.builder()
                    .billId(BILL_ID)
                    .tenantId(TENANT_ID)
                    .vendorId(VENDOR_ID)
                    .status(BillStatus.DRAFT)
                    .build();
            when(billPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(pendingPayment));
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(draftBill));

            ApplyBillPaymentRequestDto request = ApplyBillPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(BillPaymentApplicationRequestDto.builder()
                            .billId(BILL_ID)
                            .appliedAmount(11500L)
                            .build()))
                    .build();

            assertThatThrownBy(() -> billPaymentService.applyPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(InvalidBillException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("should throw when applied amount exceeds outstanding")
        void shouldThrowWhenAppliedAmountExceedsOutstanding() {
            Bill smallBill = Bill.builder()
                    .billId(BILL_ID)
                    .tenantId(TENANT_ID)
                    .vendorId(VENDOR_ID)
                    .status(BillStatus.POSTED)
                    .totalAmount(5000L)
                    .paidAmount(0L)
                    .build();
            when(billPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(pendingPayment));
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(smallBill));

            ApplyBillPaymentRequestDto request = ApplyBillPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(BillPaymentApplicationRequestDto.builder()
                            .billId(BILL_ID)
                            .appliedAmount(10000L)
                            .build()))
                    .build();

            assertThatThrownBy(() -> billPaymentService.applyPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(BillPaymentExceedsOutstandingException.class)
                    .hasMessageContaining("exceeds outstanding");
        }

        @Test
        @DisplayName("should update bill status to PAID when fully paid")
        void shouldUpdateBillStatusToPaidWhenFullyPaid() {
            when(billPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(pendingPayment));
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(postedBill));
            when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));
            doAnswer(invocation -> null)
                    .when(journalEntryService).createJournalEntry(any(UUID.class), any());

            ApplyBillPaymentRequestDto request = ApplyBillPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(BillPaymentApplicationRequestDto.builder()
                            .billId(BILL_ID)
                            .appliedAmount(11500L)
                            .build()))
                    .build();

            BillPayment result = billPaymentService.applyPayment(TENANT_ID, request, "admin");

            assertThat(result.getStatus()).isEqualTo(BillPaymentStatus.APPLIED);
            verify(billRepository).save(argThat(bill -> bill.getStatus() == BillStatus.PAID));
        }

        @Test
        @DisplayName("should update bill status to PARTIALLY_PAID when partially paid")
        void shouldUpdateBillStatusToPartiallyPaid() {
            when(billPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(pendingPayment));
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(postedBill));
            when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));
            doAnswer(invocation -> null)
                    .when(journalEntryService).createJournalEntry(any(UUID.class), any());

            ApplyBillPaymentRequestDto request = ApplyBillPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(BillPaymentApplicationRequestDto.builder()
                            .billId(BILL_ID)
                            .appliedAmount(5000L)
                            .build()))
                    .build();

            billPaymentService.applyPayment(TENANT_ID, request, "admin");

            verify(billRepository).save(argThat(bill -> bill.getStatus() == BillStatus.PARTIALLY_PAID));
        }

        @Test
        @DisplayName("should throw when applied amount exceeds outstanding balance on bill")
        void shouldThrowWhenAppliedAmountExceedsOutstandingBalance() {
            when(billPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(pendingPayment));
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(postedBill));

            // Apply more than the outstanding balance in a single application
            ApplyBillPaymentRequestDto request = ApplyBillPaymentRequestDto.builder()
                    .paymentId(PAYMENT_ID)
                    .applications(List.of(
                            BillPaymentApplicationRequestDto.builder()
                                    .billId(BILL_ID)
                                    .appliedAmount(12000L) // exceeds outstanding 11500
                                    .build()))
                    .build();

            assertThatThrownBy(() -> billPaymentService.applyPayment(TENANT_ID, request, "admin"))
                    .isInstanceOf(BillPaymentExceedsOutstandingException.class)
                    .hasMessageContaining("exceeds outstanding");
        }
    }

    // --- getPayment Tests ---

    @Nested
    @DisplayName("getPayment")
    class GetPaymentTests {

        @Test
        @DisplayName("should return payment when found")
        void shouldReturnPaymentWhenFound() {
            when(billPaymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(pendingPayment));

            BillPayment result = billPaymentService.getPayment(TENANT_ID, PAYMENT_ID);

            assertThat(result).isNotNull();
            assertThat(result.getBillPaymentId()).isEqualTo(PAYMENT_ID);
        }

        @Test
        @DisplayName("should throw when payment not in tenant")
        void shouldThrowWhenPaymentNotInTenant() {
            BillPayment wrongTenant = BillPayment.builder()
                    .billPaymentId(PAYMENT_ID)
                    .tenantId(UUID.randomUUID())
                    .build();
            when(billPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(wrongTenant));

            assertThatThrownBy(() -> billPaymentService.getPayment(TENANT_ID, PAYMENT_ID))
                    .isInstanceOf(BillNotFoundException.class);
        }
    }

    // --- getPaymentApplications Tests ---

    @Nested
    @DisplayName("getPaymentApplications")
    class GetPaymentApplicationsTests {

        @Test
        @DisplayName("should return applications for payment")
        void shouldReturnApplicationsForPayment() {
            List<BillPaymentApplication> apps = List.of(
                    BillPaymentApplication.builder()
                            .applicationId(UUID.randomUUID())
                            .billId(BILL_ID)
                            .appliedAmount(5000L)
                            .build());
            when(billPaymentApplicationRepository.findByPaymentId(PAYMENT_ID)).thenReturn(apps);

            List<BillPaymentApplication> result = billPaymentService.getPaymentApplications(PAYMENT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAppliedAmount()).isEqualTo(5000L);
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
            Page<BillPayment> page = new PageImpl<>(List.of(pendingPayment), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(billPaymentRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<BillPaymentResponseDto> result = billPaymentService.listPayments(TENANT_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // Helper for argument matcher
    @SuppressWarnings("unchecked")
    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
