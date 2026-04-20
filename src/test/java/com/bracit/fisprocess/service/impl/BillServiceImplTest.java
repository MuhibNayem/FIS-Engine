package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Bill;
import com.bracit.fisprocess.domain.entity.BillLine;
import com.bracit.fisprocess.domain.entity.BillPaymentApplication;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Vendor;
import com.bracit.fisprocess.domain.enums.BillStatus;
import com.bracit.fisprocess.domain.enums.PaymentTerms;
import com.bracit.fisprocess.dto.request.BillLineRequestDto;
import com.bracit.fisprocess.dto.request.CreateBillRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.CreateVendorRequestDto;
import com.bracit.fisprocess.dto.response.BillResponseDto;
import com.bracit.fisprocess.exception.BillAlreadyFinalizedException;
import com.bracit.fisprocess.exception.BillNotFoundException;
import com.bracit.fisprocess.exception.InvalidBillException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.exception.VendorNotFoundException;
import com.bracit.fisprocess.repository.BillLineRepository;
import com.bracit.fisprocess.repository.BillPaymentApplicationRepository;
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
import java.util.Collections;
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
@DisplayName("BillServiceImpl Unit Tests")
class BillServiceImplTest {

    @Mock
    private BillRepository billRepository;
    @Mock
    private BillLineRepository billLineRepository;
    @Mock
    private VendorRepository vendorRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private BillPaymentApplicationRepository billPaymentApplicationRepository;
    @Mock
    private JournalEntryService journalEntryService;
    @Mock
    private PeriodValidationService periodValidationService;
    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private BillServiceImpl billService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID VENDOR_ID = UUID.randomUUID();
    private static final UUID BILL_ID = UUID.randomUUID();

    private BusinessEntity activeTenant;
    private Vendor activeVendor;

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
                .currency("USD")
                .paymentTerms(PaymentTerms.NET_30)
                .status(Vendor.VendorStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // --- createVendor Tests ---

    @Nested
    @DisplayName("createVendor")
    class CreateVendorTests {

        @Test
        @DisplayName("should create vendor successfully")
        void shouldCreateVendorSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.existsByTenantIdAndCode(TENANT_ID, "VEND-001"))
                    .thenReturn(false);

            Vendor saved = Vendor.builder()
                    .vendorId(VENDOR_ID)
                    .tenantId(TENANT_ID)
                    .code("VEND-001")
                    .name("Test Vendor")
                    .status(Vendor.VendorStatus.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(vendorRepository.save(any(Vendor.class))).thenReturn(saved);

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-001")
                    .name("Test Vendor")
                    .paymentTerms(PaymentTerms.NET_30)
                    .build();

            Vendor result = billService.createVendor(TENANT_ID, request);

            assertThat(result.getCode()).isEqualTo("VEND-001");
            assertThat(result.getStatus()).isEqualTo(Vendor.VendorStatus.ACTIVE);
            verify(vendorRepository).save(any(Vendor.class));
        }

        @Test
        @DisplayName("should throw when vendor code already exists")
        void shouldThrowWhenDuplicateCode() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.existsByTenantIdAndCode(TENANT_ID, "VEND-001"))
                    .thenReturn(true);

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-001")
                    .name("Test Vendor")
                    .build();

            assertThatThrownBy(() -> billService.createVendor(TENANT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-001")
                    .name("Test Vendor")
                    .build();

            assertThatThrownBy(() -> billService.createVendor(TENANT_ID, request))
                    .isInstanceOf(TenantNotFoundException.class);
        }
    }

    // --- createBill Tests ---

    @Nested
    @DisplayName("createBill")
    class CreateBillTests {

        @Test
        @DisplayName("should create bill with lines successfully")
        void shouldCreateBillWithLines() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.findById(VENDOR_ID))
                    .thenReturn(Optional.of(activeVendor));

            Bill savedBill = Bill.builder()
                    .billId(BILL_ID)
                    .tenantId(TENANT_ID)
                    .vendorId(VENDOR_ID)
                    .billNumber("BILL-TEST-001")
                    .billDate(LocalDate.now())
                    .dueDate(LocalDate.now().plusDays(30))
                    .currency("USD")
                    .subtotalAmount(10000L)
                    .taxAmount(1500L)
                    .totalAmount(11500L)
                    .status(BillStatus.DRAFT)
                    .paidAmount(0L)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(billRepository.save(any(Bill.class))).thenReturn(savedBill);

            CreateBillRequestDto request = CreateBillRequestDto.builder()
                    .vendorId(VENDOR_ID)
                    .billNumber("BILL-001")
                    .billDate(LocalDate.now())
                    .dueDate(LocalDate.now().plusDays(30))
                    .currency("USD")
                    .lines(List.of(BillLineRequestDto.builder()
                            .description("Consulting")
                            .quantity(10L)
                            .unitPrice(1000L)
                            .taxRate(1500L)
                            .build()))
                    .build();

            Bill result = billService.createBill(TENANT_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getSubtotalAmount()).isEqualTo(10000L);
            verify(billRepository).save(any(Bill.class));
        }

        @Test
        @DisplayName("should throw when vendor not found")
        void shouldThrowWhenVendorNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.empty());

            CreateBillRequestDto request = CreateBillRequestDto.builder()
                    .vendorId(VENDOR_ID)
                    .billNumber("BILL-001")
                    .billDate(LocalDate.now())
                    .dueDate(LocalDate.now().plusDays(30))
                    .lines(List.of(BillLineRequestDto.builder()
                            .description("Test")
                            .quantity(1L)
                            .unitPrice(100L)
                            .build()))
                    .build();

            assertThatThrownBy(() -> billService.createBill(TENANT_ID, request))
                    .isInstanceOf(VendorNotFoundException.class);
        }
    }

    // --- finalizeBill Tests ---

    @Nested
    @DisplayName("finalizeBill")
    class FinalizeBillTests {

        @Test
        @DisplayName("should finalize a draft bill and post GL journal")
        void shouldFinalizeDraftBill() {
            Bill draftBill = buildDraftBill();
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(draftBill));
            when(billRepository.save(any(Bill.class))).thenReturn(draftBill);
            doAnswer(invocation -> null)
                    .when(journalEntryService).createJournalEntry(any(UUID.class), any(), any(), any());

            Bill result = billService.finalizeBill(TENANT_ID, BILL_ID, "admin");

            assertThat(result.getStatus()).isEqualTo(BillStatus.POSTED);
            verify(journalEntryService).createJournalEntry(any(UUID.class), any(CreateJournalEntryRequestDto.class));
            verify(billRepository).save(any(Bill.class));
        }

        @Test
        @DisplayName("should throw BillAlreadyFinalizedException when already posted")
        void shouldThrowWhenAlreadyFinalized() {
            Bill postedBill = buildDraftBill();
            postedBill.setStatus(BillStatus.POSTED);

            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(postedBill));

            assertThatThrownBy(() -> billService.finalizeBill(TENANT_ID, BILL_ID, "admin"))
                    .isInstanceOf(BillAlreadyFinalizedException.class);
            verify(billRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidBillException when no lines")
        void shouldThrowWhenNoLines() {
            Bill noLinesBill = buildDraftBill();
            noLinesBill.setLines(Collections.emptyList());

            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(noLinesBill));

            assertThatThrownBy(() -> billService.finalizeBill(TENANT_ID, BILL_ID, "admin"))
                    .isInstanceOf(InvalidBillException.class)
                    .hasMessageContaining("at least one line");
        }

        @Test
        @DisplayName("should throw BillNotFoundException when bill not found")
        void shouldThrowWhenBillNotFound() {
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> billService.finalizeBill(TENANT_ID, BILL_ID, "admin"))
                    .isInstanceOf(BillNotFoundException.class);
        }
    }

    // --- voidBill Tests ---

    @Nested
    @DisplayName("voidBill")
    class VoidBillTests {

        @Test
        @DisplayName("should void a draft bill")
        void shouldVoidDraftBill() {
            Bill draftBill = buildDraftBill();
            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(draftBill));
            when(billRepository.save(any(Bill.class))).thenReturn(draftBill);

            Bill result = billService.voidBill(TENANT_ID, BILL_ID, "admin");

            assertThat(result.getStatus()).isEqualTo(BillStatus.OVERDUE);
        }

        @Test
        @DisplayName("should throw when voiding bill with payments applied")
        void shouldThrowWhenPaymentsApplied() {
            Bill postedBill = buildDraftBill();
            postedBill.setStatus(BillStatus.POSTED);

            when(billRepository.findByTenantIdAndId(TENANT_ID, BILL_ID))
                    .thenReturn(Optional.of(postedBill));
            when(billPaymentApplicationRepository.findByBillId(BILL_ID))
                    .thenReturn(List.of(BillPaymentApplication.builder().build()));

            assertThatThrownBy(() -> billService.voidBill(TENANT_ID, BILL_ID, "admin"))
                    .isInstanceOf(InvalidBillException.class)
                    .hasMessageContaining("payment");
        }
    }

    // --- getVendor Tests ---

    @Nested
    @DisplayName("getVendor")
    class GetVendorTests {

        @Test
        @DisplayName("should return vendor when found")
        void shouldReturnVendorWhenFound() {
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(activeVendor));

            Vendor result = billService.getVendor(TENANT_ID, VENDOR_ID);

            assertThat(result).isNotNull();
            assertThat(result.getVendorId()).isEqualTo(VENDOR_ID);
        }

        @Test
        @DisplayName("should throw when vendor not in tenant")
        void shouldThrowWhenVendorNotInTenant() {
            Vendor wrongTenant = Vendor.builder()
                    .vendorId(VENDOR_ID)
                    .tenantId(UUID.randomUUID())
                    .build();
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(wrongTenant));

            assertThatThrownBy(() -> billService.getVendor(TENANT_ID, VENDOR_ID))
                    .isInstanceOf(VendorNotFoundException.class);
        }
    }

    // --- listVendors Tests ---

    @Nested
    @DisplayName("listVendors")
    class ListVendorsTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Vendor> page = new PageImpl<>(List.of(activeVendor), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<Vendor> result = billService.listVendors(TENANT_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // --- listBills Tests ---

    @Nested
    @DisplayName("listBills")
    class ListBillsTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Bill> page = new PageImpl<>(List.of(buildDraftBill()), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(billRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<BillResponseDto> result = billService.listBills(TENANT_ID, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // --- Helper Methods ---

    private Bill buildDraftBill() {
        BillLine line = BillLine.builder()
                .billLineId(UUID.randomUUID())
                .description("Consulting services")
                .quantity(10L)
                .unitPrice(1000L)
                .taxRate(1500L)
                .lineTotal(11500L)
                .build();

        Bill bill = Bill.builder()
                .billId(BILL_ID)
                .tenantId(TENANT_ID)
                .vendorId(VENDOR_ID)
                .billNumber("BILL-TEST-001")
                .billDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .currency("USD")
                .subtotalAmount(10000L)
                .taxAmount(1500L)
                .totalAmount(11500L)
                .status(BillStatus.DRAFT)
                .paidAmount(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        bill.addLine(line);
        return bill;
    }
}
