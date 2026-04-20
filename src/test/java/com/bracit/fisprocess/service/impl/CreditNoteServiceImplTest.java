package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.CreditNote;
import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.enums.CreditNoteStatus;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.dto.request.CreateCreditNoteRequestDto;
import com.bracit.fisprocess.dto.response.CreditNoteResponseDto;
import com.bracit.fisprocess.exception.CreditNoteExceedsInvoiceException;
import com.bracit.fisprocess.exception.InvalidInvoiceException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.CreditNoteRepository;
import com.bracit.fisprocess.repository.CustomerRepository;
import com.bracit.fisprocess.repository.InvoiceRepository;
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
@DisplayName("CreditNoteServiceImpl Unit Tests")
class CreditNoteServiceImplTest {

    @Mock
    private CreditNoteRepository creditNoteRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
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
    private CreditNoteServiceImpl creditNoteService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CREDIT_NOTE_ID = UUID.randomUUID();

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

    // --- createCreditNote Tests ---

    @Nested
    @DisplayName("createCreditNote")
    class CreateCreditNoteTests {

        @Test
        @DisplayName("should create credit note successfully")
        void shouldCreateCreditNoteSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.findById(CUSTOMER_ID))
                    .thenReturn(Optional.of(Customer.builder()
                            .customerId(CUSTOMER_ID)
                            .tenantId(TENANT_ID)
                            .build()));

            Invoice invoice = buildPostedInvoice(10000L, 1500L); // outstanding = 8500
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(invoice));

            CreditNote saved = buildCreditNote(CreditNoteStatus.DRAFT);
            when(creditNoteRepository.save(any(CreditNote.class))).thenReturn(saved);

            CreateCreditNoteRequestDto request = CreateCreditNoteRequestDto.builder()
                    .customerId(CUSTOMER_ID)
                    .originalInvoiceId(INVOICE_ID)
                    .amount(5000L)
                    .reason("Damaged goods")
                    .build();

            CreditNote result = creditNoteService.createCreditNote(TENANT_ID, request, "admin");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(CreditNoteStatus.DRAFT);
            assertThat(result.getAmount()).isEqualTo(5000L);
            verify(creditNoteRepository).save(any(CreditNote.class));
        }

        @Test
        @DisplayName("should throw when credit note exceeds invoice outstanding")
        void shouldThrowWhenExceedsInvoiceOutstanding() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.findById(CUSTOMER_ID))
                    .thenReturn(Optional.of(Customer.builder()
                            .customerId(CUSTOMER_ID)
                            .tenantId(TENANT_ID)
                            .build()));

            Invoice invoice = buildPostedInvoice(10000L, 1500L); // outstanding = 8500
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(invoice));

            CreateCreditNoteRequestDto request = CreateCreditNoteRequestDto.builder()
                    .customerId(CUSTOMER_ID)
                    .originalInvoiceId(INVOICE_ID)
                    .amount(15000L) // exceeds outstanding
                    .reason("Over-credit")
                    .build();

            assertThatThrownBy(() -> creditNoteService.createCreditNote(TENANT_ID, request, "admin"))
                    .isInstanceOf(CreditNoteExceedsInvoiceException.class);
            verify(creditNoteRepository, never()).save(any());
        }
    }

    // --- applyCreditNote Tests ---

    @Nested
    @DisplayName("applyCreditNote")
    class ApplyCreditNoteTests {

        @Test
        @DisplayName("should apply credit note and post GL journal")
        void shouldApplyCreditNoteSuccessfully() {
            CreditNote creditNote = buildCreditNote(CreditNoteStatus.DRAFT);
            Invoice invoice = buildPostedInvoice(10000L, 1500L);

            when(creditNoteRepository.findById(CREDIT_NOTE_ID))
                    .thenReturn(Optional.of(creditNote));
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(invoice));
            when(creditNoteRepository.save(any(CreditNote.class))).thenReturn(creditNote);
            when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);
            // Simulate default method delegation: 2-arg → 4-arg
            doAnswer(invocation -> {
                UUID tid = invocation.getArgument(0);
                com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto req = invocation.getArgument(1);
                return journalEntryService.createJournalEntry(tid, req, null, null);
            }).when(journalEntryService).createJournalEntry(any(UUID.class), any());
            when(journalEntryService.createJournalEntry(any(UUID.class), any(), any(), any()))
                    .thenReturn(null);

            CreditNote result = creditNoteService.applyCreditNote(TENANT_ID, CREDIT_NOTE_ID, "admin");

            assertThat(result.getStatus()).isEqualTo(CreditNoteStatus.APPLIED);
            verify(journalEntryService).createJournalEntry(any(UUID.class), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when credit note is not in DRAFT status")
        void shouldThrowWhenNotDraft() {
            CreditNote appliedNote = buildCreditNote(CreditNoteStatus.APPLIED);

            when(creditNoteRepository.findById(CREDIT_NOTE_ID))
                    .thenReturn(Optional.of(appliedNote));

            assertThatThrownBy(() -> creditNoteService.applyCreditNote(TENANT_ID, CREDIT_NOTE_ID, "admin"))
                    .isInstanceOf(InvalidInvoiceException.class)
                    .hasMessageContaining("not in DRAFT status");
            verify(journalEntryService, never()).createJournalEntry(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when applied amount exceeds remaining outstanding")
        void shouldThrowWhenExceedsRemainingOutstanding() {
            CreditNote creditNote = buildCreditNote(CreditNoteStatus.DRAFT);
            // Invoice already mostly paid — outstanding is less than credit note
            Invoice invoice = buildPostedInvoice(10000L, 1500L);
            invoice.setPaidAmount(9000L); // outstanding = 1000

            when(creditNoteRepository.findById(CREDIT_NOTE_ID))
                    .thenReturn(Optional.of(creditNote));
            when(invoiceRepository.findByTenantIdAndId(TENANT_ID, INVOICE_ID))
                    .thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> creditNoteService.applyCreditNote(TENANT_ID, CREDIT_NOTE_ID, "admin"))
                    .isInstanceOf(CreditNoteExceedsInvoiceException.class);
        }
    }

    // --- listCreditNotes Tests ---

    @Nested
    @DisplayName("listCreditNotes")
    class ListCreditNotesTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<CreditNote> page = new PageImpl<>(
                    List.of(buildCreditNote(CreditNoteStatus.DRAFT)), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(creditNoteRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<CreditNoteResponseDto> result = creditNoteService.listCreditNotes(
                    TENANT_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // --- Helpers ---

    private CreditNote buildCreditNote(CreditNoteStatus status) {
        return CreditNote.builder()
                .creditNoteId(CREDIT_NOTE_ID)
                .tenantId(TENANT_ID)
                .customerId(CUSTOMER_ID)
                .originalInvoiceId(INVOICE_ID)
                .amount(5000L)
                .reason("Damaged goods")
                .status(status)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private Invoice buildPostedInvoice(long total, long tax) {
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
