package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.entity.InvoiceLine;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.dto.request.CreateCustomerRequestDto;
import com.bracit.fisprocess.dto.request.CreateInvoiceRequestDto;
import com.bracit.fisprocess.dto.request.InvoiceLineRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.response.InvoiceResponseDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
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
import com.bracit.fisprocess.service.InvoiceService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PeriodValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link InvoiceService} for AR Invoice and Customer operations.
 * <p>
 * Enforces all business rules including GL journal posting on finalization,
 * tenant scoping, and invoice validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final CustomerRepository customerRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final PaymentApplicationRepository paymentApplicationRepository;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper modelMapper;

    @Value("${fis.ar.tax-account-code:TAX_PAYABLE}")
    private String taxAccountCode;

    @Override
    @Transactional
    public Customer createCustomer(UUID tenantId, CreateCustomerRequestDto request) {
        validateTenantExists(tenantId);
        validateCustomerCodeUniqueness(tenantId, request.getCode());

        Customer customer = Customer.builder()
                .tenantId(tenantId)
                .code(request.getCode().trim())
                .name(request.getName().trim())
                .email(request.getEmail())
                .currency(request.getCurrency())
                .creditLimit(request.getCreditLimit())
                .status(Customer.CustomerStatus.ACTIVE)
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Created customer '{}' for tenant '{}'", saved.getCode(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public Invoice createInvoice(UUID tenantId, CreateInvoiceRequestDto request) {
        validateTenantExists(tenantId);
        validateCustomerExists(tenantId, request.getCustomerId());

        // Generate invoice number: INV-{tenantId-short}-{timestamp}
        String invoiceNumber = generateInvoiceNumber(tenantId);

        Invoice invoice = Invoice.builder()
                .tenantId(tenantId)
                .customerId(request.getCustomerId())
                .invoiceNumber(invoiceNumber)
                .issueDate(request.getIssueDate())
                .dueDate(request.getDueDate())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .referenceId(request.getReferenceId())
                .status(InvoiceStatus.DRAFT)
                .build();

        // Build lines and calculate totals
        long subtotalAmount = 0L;
        long taxAmount = 0L;
        int sortIdx = 0;

        for (InvoiceLineRequestDto lineDto : request.getLines()) {
            long lineSubtotal = lineDto.getQuantity() * lineDto.getUnitPrice();
            long lineTax = BigDecimalMath.multiplyBasisPoints(lineSubtotal, lineDto.getTaxRate());
            long lineTotal = lineSubtotal + lineTax;

            InvoiceLine line = InvoiceLine.builder()
                    .description(lineDto.getDescription())
                    .quantity(lineDto.getQuantity())
                    .unitPrice(lineDto.getUnitPrice())
                    .taxRate(lineDto.getTaxRate())
                    .lineTotal(lineTotal)
                    .glAccountId(lineDto.getGlAccountId())
                    .sortOrder(lineDto.getSortOrder() != null ? lineDto.getSortOrder() : sortIdx++)
                    .build();

            invoice.addLine(line);
            subtotalAmount += lineSubtotal;
            taxAmount += lineTax;
        }

        invoice.setSubtotalAmount(subtotalAmount);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(subtotalAmount + taxAmount);

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Created draft invoice '{}' for tenant '{}'", saved.getInvoiceNumber(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public Invoice finalizeInvoice(UUID tenantId, UUID invoiceId, String performedBy) {
        Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);

        // BR3: Cannot finalize already-finalized invoice
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvoiceAlreadyFinalizedException(invoiceId);
        }

        // BR2: Cannot finalize with no lines
        if (invoice.getLines().isEmpty()) {
            throw new InvalidInvoiceException(invoiceId, "Invoice must have at least one line to finalize");
        }

        // BR1: Validate total equals sum of line totals
        validateInvoiceTotals(invoice);

        // FR-11: Validate accounting period is OPEN before posting to GL
        periodValidationService.validatePostingAllowed(tenantId, invoice.getIssueDate(), null);

        // Post journal to GL
        postInvoiceJournal(invoice, performedBy);

        // Transition to POSTED
        invoice.setStatus(InvoiceStatus.POSTED);
        Invoice updated = invoiceRepository.save(invoice);

        log.info("Finalized invoice '{}' for tenant '{}' — GL journal posted", invoiceId, tenantId);
        return updated;
    }

    @Override
    @Transactional
    public Invoice voidInvoice(UUID tenantId, UUID invoiceId, String performedBy) {
        Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);

        // BR4: Cannot void a finalized invoice that has payments applied
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            List<com.bracit.fisprocess.domain.entity.PaymentApplication> applications =
                    paymentApplicationRepository.findByInvoiceId(invoiceId);
            if (!applications.isEmpty()) {
                throw new InvalidInvoiceException(invoiceId,
                        "Cannot void invoice with " + applications.size() + " payment(s) applied");
            }
            throw new InvalidInvoiceException(invoiceId,
                    "Cannot void a non-draft invoice. Use a Credit Note for corrections.");
        }

        // Post write-off journal to GL
        periodValidationService.validatePostingAllowed(tenantId, LocalDate.now(), null);
        postWriteOffJournal(invoice, performedBy);

        invoice.setStatus(InvoiceStatus.WRITTEN_OFF);
        Invoice updated = invoiceRepository.save(invoice);

        log.info("Voided invoice '{}' for tenant '{}' — write-off GL journal posted", invoiceId, tenantId);
        return updated;
    }

    @Override
    public Invoice getInvoice(UUID tenantId, UUID invoiceId) {
        return getInvoiceOrThrow(tenantId, invoiceId);
    }

    @Override
    public Customer getCustomer(UUID tenantId, UUID customerId) {
        return customerRepository.findById(customerId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    @Override
    public Page<Customer> listCustomers(
            UUID tenantId,
            @Nullable String search,
            Pageable pageable) {
        validateTenantExists(tenantId);
        return customerRepository.findByTenantIdWithFilters(tenantId, search, pageable);
    }

    @Override
    public Page<InvoiceResponseDto> listInvoices(
            UUID tenantId,
            @Nullable UUID customerId,
            @Nullable InvoiceStatus status,
            Pageable pageable) {
        validateTenantExists(tenantId);

        return invoiceRepository.findByTenantIdWithFilters(tenantId, customerId, status, pageable)
                .map(this::toResponseDto);
    }

    @Override
    public List<InvoiceLine> getInvoiceLines(UUID invoiceId) {
        return invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    }

    // --- Private Helper Methods ---

    private Invoice getInvoiceOrThrow(UUID tenantId, UUID invoiceId) {
        return invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
    }

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private void validateCustomerCodeUniqueness(UUID tenantId, String code) {
        if (customerRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException(
                    "Customer code '" + code + "' already exists for this tenant");
        }
    }

    private void validateCustomerExists(UUID tenantId, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        // Verify tenant ownership
        if (!customer.getTenantId().equals(tenantId)) {
            throw new CustomerNotFoundException(customerId);
        }
    }

    private String generateInvoiceNumber(UUID tenantId) {
        String shortTenant = tenantId.toString().substring(0, 8).toUpperCase();
        return "INV-" + shortTenant + "-" + System.currentTimeMillis();
    }

    /**
     * BR1: Validates that invoice total equals sum of line totals (including tax).
     */
    private void validateInvoiceTotals(Invoice invoice) {
        long computedSubtotal = 0L;
        long computedTax = 0L;
        long computedTotal = 0L;

        for (InvoiceLine line : invoice.getLines()) {
            long lineSubtotal = line.getQuantity() * line.getUnitPrice();
            long lineTax = BigDecimalMath.multiplyBasisPoints(lineSubtotal, line.getTaxRate());
            computedSubtotal += lineSubtotal;
            computedTax += lineTax;
            computedTotal += line.getLineTotal();
        }

        long expectedTotal = computedSubtotal + computedTax;
        if (Math.abs(invoice.getTotalAmount() - expectedTotal) > 1) {
            throw new InvalidInvoiceException(invoice.getInvoiceId(),
                    "Total amount mismatch: expected " + expectedTotal + ", got " + invoice.getTotalAmount());
        }
    }

    /**
     * Posts the journal entry for invoice finalization.
     * <pre>
     * DEBIT:  Accounts Receivable  = totalAmount
     * CREDIT: Sales Revenue (per line's glAccountId or default) = subtotalAmount
     * CREDIT: Tax Payable           = taxAmount
     * </pre>
     */
    private void postInvoiceJournal(Invoice invoice, String performedBy) {
        try {
            List<JournalLineRequestDto> journalLines = new ArrayList<>();

            // DEBIT: Accounts Receivable = totalAmount
            journalLines.add(JournalLineRequestDto.builder()
                    .accountCode("AR-" + invoice.getCustomerId().toString().substring(0, 8).toUpperCase())
                    .amountCents(invoice.getTotalAmount())
                    .isCredit(false)
                    .build());

            // CREDIT: Sales Revenue per line
            for (InvoiceLine line : invoice.getLines()) {
                long lineSubtotal = line.getQuantity() * line.getUnitPrice();
                // If no GL account specified on line, skip — revenue goes to a default
                String revenueAccountCode = line.getGlAccountId() != null
                        ? line.getGlAccountId().toString().substring(0, 8).toUpperCase()
                        : "SALES_REVENUE";
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(revenueAccountCode)
                        .amountCents(lineSubtotal)
                        .isCredit(true)
                        .build());
            }

            // CREDIT: Tax Payable = taxAmount
            if (invoice.getTaxAmount() > 0) {
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(taxAccountCode)
                        .amountCents(invoice.getTaxAmount())
                        .isCredit(true)
                        .build());
            }

            String eventId = "AR-" + invoice.getInvoiceId() + "-FINALIZE";

            journalEntryService.createJournalEntry(
                    invoice.getTenantId(),
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(invoice.getIssueDate())
                            .transactionDate(invoice.getIssueDate())
                            .description("Invoice " + invoice.getInvoiceNumber() + " finalization")
                            .referenceId("INV-" + invoice.getInvoiceId())
                            .transactionCurrency(invoice.getCurrency())
                            .createdBy(performedBy)
                            .lines(journalLines)
                            .build());

        } catch (Exception ex) {
            log.error("Failed to post GL journal for invoice '{}': {}", invoice.getInvoiceId(), ex.getMessage(), ex);
            throw new InvalidInvoiceException(invoice.getInvoiceId(),
                    "Failed to post journal entry to GL: " + ex.getMessage());
        }
    }

    /**
     * Posts the journal entry for invoice write-off (void).
     * <pre>
     * DEBIT:  Bad Debt Expense  = totalAmount
     * CREDIT: Accounts Receivable = totalAmount
     * </pre>
     */
    private void postWriteOffJournal(Invoice invoice, String performedBy) {
        try {
            String eventId = "AR-WRITE_OFF-" + invoice.getInvoiceId();

            journalEntryService.createJournalEntry(
                    invoice.getTenantId(),
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(LocalDate.now())
                            .transactionDate(LocalDate.now())
                            .description("Write-off for invoice " + invoice.getInvoiceNumber())
                            .referenceId("INV-" + invoice.getInvoiceId() + "-WRITE_OFF")
                            .transactionCurrency(invoice.getCurrency())
                            .createdBy(performedBy)
                            .lines(List.of(
                                    JournalLineRequestDto.builder()
                                            .accountCode("BAD_DEBT_EXPENSE")
                                            .amountCents(invoice.getTotalAmount())
                                            .isCredit(false)
                                            .build(),
                                    JournalLineRequestDto.builder()
                                            .accountCode("AR-" + invoice.getCustomerId().toString().substring(0, 8).toUpperCase())
                                            .amountCents(invoice.getTotalAmount())
                                            .isCredit(true)
                                            .build()))
                            .build());

            log.info("Posted write-off journal for invoice '{}'", invoice.getInvoiceId());
        } catch (Exception ex) {
            log.error("Failed to post GL journal for write-off of invoice '{}': {}", invoice.getInvoiceId(), ex.getMessage(), ex);
            throw new InvalidInvoiceException(invoice.getInvoiceId(),
                    "Failed to post write-off journal to GL: " + ex.getMessage());
        }
    }

    private InvoiceResponseDto toResponseDto(Invoice invoice) {
        InvoiceResponseDto dto = modelMapper.map(invoice, InvoiceResponseDto.class);
        dto.setInvoiceId(invoice.getInvoiceId());
        dto.setOutstandingAmount(invoice.getOutstandingAmount());
        dto.setIssueDate(invoice.getIssueDate().toString());
        dto.setDueDate(invoice.getDueDate().toString());
        dto.setStatus(invoice.getStatus().name());

        if (invoice.getLines() != null && !invoice.getLines().isEmpty()) {
            dto.setLines(invoice.getLines().stream()
                    .map(this::toLineResponseDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private com.bracit.fisprocess.dto.response.InvoiceLineResponseDto toLineResponseDto(InvoiceLine line) {
        com.bracit.fisprocess.dto.response.InvoiceLineResponseDto dto =
                modelMapper.map(line, com.bracit.fisprocess.dto.response.InvoiceLineResponseDto.class);
        dto.setInvoiceLineId(line.getInvoiceLineId());
        return dto;
    }

    /**
     * Utility class for basis-point arithmetic without floating-point.
     */
    private static final class BigDecimalMath {
        private BigDecimalMath() {}

        /**
         * Multiplies a value by a rate expressed in basis points (e.g., 1500 = 15%).
         */
        static long multiplyBasisPoints(long value, long basisPoints) {
            return (value * basisPoints) / 10_000L;
        }
    }
}
