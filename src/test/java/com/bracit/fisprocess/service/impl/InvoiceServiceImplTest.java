package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.entity.InvoiceLine;
import com.bracit.fisprocess.domain.entity.PaymentApplication;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.dto.request.CreateCustomerRequestDto;
import com.bracit.fisprocess.dto.request.CreateInvoiceRequestDto;
import com.bracit.fisprocess.dto.request.InvoiceLineRequestDto;
import com.bracit.fisprocess.dto.response.InvoiceResponseDto;
import com.bracit.fisprocess.exception.CustomerNotFoundException;
import com.bracit.fisprocess.exception.InvalidInvoiceException;
import com.bracit.fisprocess.exception.InvoiceAlreadyFinalizedException;
import com.bracit.fisprocess.exception.InvoiceNotFoundException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.CustomerRepository;
import com.bracit.fisprocess.repository.InvoiceLineRepository;
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
@DisplayName("InvoiceServiceImpl Unit Tests")
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceLineRepository invoiceLineRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private PaymentApplicationRepository paymentApplicationRepository;
    @Mock
    private JournalEntryService journalEntryService;
    @Mock
    private PeriodValidationService periodValidationService;
    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
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
    }

    // --- createCustomer Tests ---

    @Nested
    @DisplayName("createCustomer")
    class CreateCustomerTests {

        @Test
        @DisplayName("should create customer successfully")
        void shouldCreateCustomerSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.existsByTenantIdAndCode(TENANT_ID, "CUST-001"))
                    .thenReturn(false);

            Customer saved = Customer.builder()
                    .customerId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code("CUST-001")
                    .name("Test Customer")
                    .currency("USD")
                    .creditLimit(0L)
                    .status(Customer.CustomerStatus.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(customerRepository.save(any(Customer.class))).thenReturn(saved);

            CreateCustomerRequestDto request = CreateCustomerRequestDto.builder()
                    .code("CUST-001")
                    .name("Test Customer")
                    .build();

            Customer result = invoiceService.createCustomer(TENANT_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("CUST-001");
            assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("should throw when customer code already exists")
        void shouldThrowWhenDuplicateCode() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.existsByTenantIdAndCode(TENANT_ID, "CUST-001"))
                    .thenReturn(true);

            CreateCustomerRequestDto request = CreateCustomerRequestDto.builder()
                    .code("CUST-001")
                    .name("Test Customer")
                    .build();

            assertThatThrownBy(() -> invoiceService.createCustomer(TENANT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            CreateCustomerRequestDto request = CreateCustomerRequestDto.builder()
                    .code("CUST-001")
                    .name("Test Customer")
                    .build();

            assertThatThrownBy(() -> invoiceService.createCustomer(TENANT_ID, request))
                    .isInstanceOf(TenantNotFoundException.class);
        }
    }

    // --- createInvoice Tests ---

    @Nested
    @DisplayName("createInvoice")
    class CreateInvoiceTests {

        @Test
        @DisplayName("should create invoice with lines successfully")
        void shouldCreateInvoiceWithLines() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.findById(CUSTOMER_ID))
                    .thenReturn(Optional.of(Customer.builder()
                            .customerId(CUSTOMER_ID)
                            .tenantId(TENANT_ID)
                            .build()));

            Invoice savedInvoice = Invoice.builder()
                    .invoiceId(INVOICE_ID)
                    .tenantId(TENANT_ID)
                    .customerId(CUSTOMER_ID)
                    .invoiceNumber("INV-TEST-001")
                    .issueDate(LocalDate.now())
                    .dueDate(LocalDate.now().plusDays(30))
                    .currency("USD")
                    .subtotalAmount(10000L)
                    .taxAmount(1500L)
                    .totalAmount(11500L)
                    .status(com.bracit.fisprocess.domain.enums.InvoiceStatus.DRAFT)
                    .paidAmount(0L)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);

            CreateInvoiceRequestDto request = CreateInvoiceRequestDto.builder()
                    .customerId(CUSTOMER_ID)
                    .issueDate(LocalDate.now())
                    .dueDate(LocalDate.now().plusDays(30))
                    .currency("USD")
                    .lines(List.of(InvoiceLineRequestDto.builder()
                            .description("Consulting")
                            .quantity(10L)
                            .unitPrice(1000L)
                            .taxRate(1500L)
                            .build()))
                    .build();

            Invoice result = invoiceService.createInvoice(TENANT_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getSubtotalAmount()).isEqualTo(10000L);
            verify(invoiceRepository).save(any(Invoice.class));
        }
    }

    // --- finalizeInvoice Tests ---

    @Nested
    @DisplayName("finalizeInvoice")
    class FinalizeInvoiceTests {

        @Test
        @DisplayName("should finalize a draft invoice and post GL journal")
        void shouldFinalizeDraftInvoice() {
            Invoice draftInvoice = buildDraftInvoice();
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(draftInvoice));
            when(invoiceRepository.save(any(Invoice.class))).thenReturn(draftInvoice);
            // Simulate default method delegation: 2-arg → 4-arg
            doAnswer(invocation -> {
                UUID tid = invocation.getArgument(0);
                com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto req = invocation.getArgument(1);
                return journalEntryService.createJournalEntry(tid, req, null, null);
            }).when(journalEntryService).createJournalEntry(any(UUID.class), any());
            when(journalEntryService.createJournalEntry(any(UUID.class), any(), any(), any()))
                    .thenReturn(null);

            Invoice result = invoiceService.finalizeInvoice(TENANT_ID, INVOICE_ID, "admin");

            assertThat(result.getStatus()).isEqualTo(com.bracit.fisprocess.domain.enums.InvoiceStatus.POSTED);
            verify(journalEntryService).createJournalEntry(any(UUID.class), any(), any(), any());
            verify(invoiceRepository).save(any(Invoice.class));
        }

        @Test
        @DisplayName("should throw InvoiceAlreadyFinalizedException when already posted")
        void shouldThrowWhenAlreadyFinalized() {
            Invoice postedInvoice = buildDraftInvoice();
            postedInvoice.setStatus(com.bracit.fisprocess.domain.enums.InvoiceStatus.POSTED);

            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(postedInvoice));

            assertThatThrownBy(() -> invoiceService.finalizeInvoice(TENANT_ID, INVOICE_ID, "admin"))
                    .isInstanceOf(InvoiceAlreadyFinalizedException.class);
            verify(invoiceRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidInvoiceException when no lines")
        void shouldThrowWhenNoLines() {
            Invoice noLinesInvoice = buildDraftInvoice();
            noLinesInvoice.setLines(Collections.emptyList());

            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(noLinesInvoice));

            assertThatThrownBy(() -> invoiceService.finalizeInvoice(TENANT_ID, INVOICE_ID, "admin"))
                    .isInstanceOf(InvalidInvoiceException.class)
                    .hasMessageContaining("at least one line");
        }

        @Test
        @DisplayName("should throw InvoiceNotFoundException when invoice not found")
        void shouldThrowWhenInvoiceNotFound() {
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> invoiceService.finalizeInvoice(TENANT_ID, INVOICE_ID, "admin"))
                    .isInstanceOf(InvoiceNotFoundException.class);
        }
    }

    // --- voidInvoice Tests ---

    @Nested
    @DisplayName("voidInvoice")
    class VoidInvoiceTests {

        @Test
        @DisplayName("should void a draft invoice")
        void shouldVoidDraftInvoice() {
            Invoice draftInvoice = buildDraftInvoice();
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(draftInvoice));
            when(invoiceRepository.save(any(Invoice.class))).thenReturn(draftInvoice);

            Invoice result = invoiceService.voidInvoice(TENANT_ID, INVOICE_ID, "admin");

            assertThat(result.getStatus()).isEqualTo(com.bracit.fisprocess.domain.enums.InvoiceStatus.WRITTEN_OFF);
        }

        @Test
        @DisplayName("should throw when voiding invoice with payments applied")
        void shouldThrowWhenPaymentsApplied() {
            Invoice postedInvoice = buildDraftInvoice();
            postedInvoice.setStatus(com.bracit.fisprocess.domain.enums.InvoiceStatus.POSTED);

            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(postedInvoice));
            when(paymentApplicationRepository.findByInvoiceId(INVOICE_ID))
                    .thenReturn(List.of(PaymentApplication.builder().build()));

            assertThatThrownBy(() -> invoiceService.voidInvoice(TENANT_ID, INVOICE_ID, "admin"))
                    .isInstanceOf(InvalidInvoiceException.class)
                    .hasMessageContaining("payment");
        }
    }

    // --- listInvoices Tests ---

    @Nested
    @DisplayName("listInvoices")
    class ListInvoicesTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Invoice> page = new PageImpl<>(List.of(buildDraftInvoice()), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(invoiceRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<InvoiceResponseDto> result = invoiceService.listInvoices(
                    TENANT_ID, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // --- Helper Methods ---

    private Invoice buildDraftInvoice() {
        InvoiceLine line = InvoiceLine.builder()
                .invoiceLineId(UUID.randomUUID())
                .description("Consulting services")
                .quantity(10L)
                .unitPrice(1000L)
                .taxRate(1500L)
                .lineTotal(11500L)
                .build();

        Invoice invoice = Invoice.builder()
                .invoiceId(INVOICE_ID)
                .tenantId(TENANT_ID)
                .customerId(CUSTOMER_ID)
                .invoiceNumber("INV-TEST-001")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .currency("USD")
                .subtotalAmount(10000L)
                .taxAmount(1500L)
                .totalAmount(11500L)
                .status(com.bracit.fisprocess.domain.enums.InvoiceStatus.DRAFT)
                .paidAmount(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        invoice.addLine(line);
        return invoice;
    }
}
