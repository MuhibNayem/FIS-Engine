package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.CreditNote;
import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.enums.CreditNoteStatus;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.dto.request.CreateCreditNoteRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.response.CreditNoteResponseDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.exception.CreditNoteExceedsInvoiceException;
import com.bracit.fisprocess.exception.CustomerNotFoundException;
import com.bracit.fisprocess.exception.InvoiceNotFoundException;
import com.bracit.fisprocess.exception.InvalidInvoiceException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.CreditNoteRepository;
import com.bracit.fisprocess.repository.CustomerRepository;
import com.bracit.fisprocess.repository.InvoiceRepository;
import com.bracit.fisprocess.service.CreditNoteService;
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
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link CreditNoteService} for AR Credit Note operations.
 * <p>
 * Enforces business rules including outstanding balance validation,
 * GL reversal journal posting, and tenant scoping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CreditNoteServiceImpl implements CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper modelMapper;

    @Value("${fis.ar.tax-account-code:TAX_PAYABLE}")
    private String taxAccountCode;

    @Override
    @Transactional
    public CreditNote createCreditNote(UUID tenantId, CreateCreditNoteRequestDto request, String performedBy) {
        validateTenantExists(tenantId);
        validateCustomerExists(tenantId, request.getCustomerId());

        // Validate the original invoice exists and belongs to the tenant
        Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, request.getOriginalInvoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException(request.getOriginalInvoiceId()));

        // BR7: Credit note amount cannot exceed invoice's outstanding amount
        Long outstanding = invoice.getOutstandingAmount();
        if (request.getAmount() > outstanding) {
            throw new CreditNoteExceedsInvoiceException(
                    // We don't have the credit note ID yet, use a placeholder
                    java.util.UUID.randomUUID(), request.getOriginalInvoiceId());
        }

        CreditNote creditNote = CreditNote.builder()
                .tenantId(tenantId)
                .customerId(request.getCustomerId())
                .originalInvoiceId(request.getOriginalInvoiceId())
                .amount(request.getAmount())
                .reason(request.getReason())
                .status(CreditNoteStatus.DRAFT)
                .build();

        CreditNote saved = creditNoteRepository.save(creditNote);
        log.info("Created credit note '{}' for invoice '{}' tenant '{}'",
                saved.getCreditNoteId(), request.getOriginalInvoiceId(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public CreditNote applyCreditNote(UUID tenantId, UUID creditNoteId, String performedBy) {
        CreditNote creditNote = getCreditNoteOrThrow(tenantId, creditNoteId);

        if (creditNote.getStatus() != CreditNoteStatus.DRAFT) {
            throw new InvalidInvoiceException(creditNoteId,
                    "Credit note is not in DRAFT status — current status: " + creditNote.getStatus());
        }

        // Validate the original invoice
        Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, creditNote.getOriginalInvoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException(creditNote.getOriginalInvoiceId()));

        if (invoice.getStatus() != InvoiceStatus.POSTED
                && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
            throw new InvalidInvoiceException(invoice.getInvoiceId(),
                    "Cannot apply credit note to invoice in status: " + invoice.getStatus());
        }

        // BR7 (re-check): Credit note amount cannot exceed outstanding
        Long outstanding = invoice.getOutstandingAmount();
        if (creditNote.getAmount() > outstanding) {
            throw new CreditNoteExceedsInvoiceException(creditNoteId, invoice.getInvoiceId());
        }

        // FR-11: Validate accounting period is OPEN before posting to GL
        periodValidationService.validatePostingAllowed(tenantId, LocalDate.now(), null);

        // Post reversal journal to GL
        postCreditNoteJournal(creditNote, invoice, performedBy);

        // Update credit note status
        creditNote.setStatus(CreditNoteStatus.APPLIED);

        // Update invoice paid amount (credit reduces outstanding)
        invoice.setPaidAmount(invoice.getPaidAmount() + creditNote.getAmount());
        if (invoice.getOutstandingAmount() <= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoiceRepository.save(invoice);

        CreditNote updated = creditNoteRepository.save(creditNote);

        log.info("Applied credit note '{}' for invoice '{}' tenant '{}'", creditNoteId,
                creditNote.getOriginalInvoiceId(), tenantId);
        return updated;
    }

    @Override
    public CreditNote getCreditNote(UUID tenantId, UUID creditNoteId) {
        return getCreditNoteOrThrow(tenantId, creditNoteId);
    }

    @Override
    public Page<CreditNoteResponseDto> listCreditNotes(
            UUID tenantId,
            @Nullable UUID customerId,
            Pageable pageable) {
        validateTenantExists(tenantId);

        return creditNoteRepository.findByTenantIdWithFilters(tenantId, customerId, null, pageable)
                .map(this::toResponseDto);
    }

    // --- Private Helper Methods ---

    private CreditNote getCreditNoteOrThrow(UUID tenantId, UUID creditNoteId) {
        return creditNoteRepository.findById(creditNoteId)
                .filter(cn -> cn.getTenantId().equals(tenantId))
                .orElseThrow(() -> new InvoiceNotFoundException(creditNoteId));
    }

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private void validateCustomerExists(UUID tenantId, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (!customer.getTenantId().equals(tenantId)) {
            throw new CustomerNotFoundException(customerId);
        }
    }

    /**
     * Posts the reversal journal entry for a credit note.
     * <pre>
     * DEBIT:  Sales Revenue     = amount (revenue portion)
     * DEBIT:  Tax Payable       = taxPortion
     * CREDIT: Accounts Receivable = totalCreditNoteAmount
     * </pre>
     * <p>
     * Since we don't have the exact tax breakdown from the original invoice lines
     * here, we apply a simplified approach: the full amount goes to AR credit,
     * with revenue and tax debits proportionally split.
     */
    private void postCreditNoteJournal(CreditNote creditNote, Invoice invoice, String performedBy) {
        try {
            // Calculate proportional split between revenue and tax
            long totalInvoice = invoice.getTotalAmount();
            long revenuePortion = totalInvoice > 0
                    ? (creditNote.getAmount() * invoice.getSubtotalAmount()) / totalInvoice
                    : creditNote.getAmount();
            long taxPortion = creditNote.getAmount() - revenuePortion;

            String eventId = "AR-CN-" + creditNote.getCreditNoteId() + "-APPLY";

            List<JournalLineRequestDto> journalLines = List.of(
                    // DEBIT: Sales Revenue
                    JournalLineRequestDto.builder()
                            .accountCode("SALES_REVENUE")
                            .amountCents(revenuePortion)
                            .isCredit(false)
                            .build(),
                    // DEBIT: Tax Payable (if any)
                    JournalLineRequestDto.builder()
                            .accountCode(taxAccountCode)
                            .amountCents(taxPortion)
                            .isCredit(false)
                            .build(),
                    // CREDIT: Accounts Receivable
                    JournalLineRequestDto.builder()
                            .accountCode("AR-" + invoice.getCustomerId().toString().substring(0, 8).toUpperCase())
                            .amountCents(creditNote.getAmount())
                            .isCredit(true)
                            .build());

            journalEntryService.createJournalEntry(
                    invoice.getTenantId(),
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(LocalDate.now())
                            .transactionDate(LocalDate.now())
                            .description("Credit note " + creditNote.getCreditNoteId()
                                    + " applied to invoice " + invoice.getInvoiceNumber())
                            .referenceId("CN-" + creditNote.getCreditNoteId())
                            .transactionCurrency(invoice.getCurrency())
                            .createdBy(performedBy)
                            .lines(journalLines)
                            .build());
        } catch (Exception ex) {
            log.error("Failed to post GL journal for credit note '{}': {}",
                    creditNote.getCreditNoteId(), ex.getMessage(), ex);
            throw new InvalidInvoiceException(creditNote.getCreditNoteId(),
                    "Failed to post credit note journal to GL: " + ex.getMessage());
        }
    }

    private CreditNoteResponseDto toResponseDto(CreditNote creditNote) {
        CreditNoteResponseDto dto = modelMapper.map(creditNote, CreditNoteResponseDto.class);
        dto.setCreditNoteId(creditNote.getCreditNoteId());
        dto.setStatus(creditNote.getStatus().name());
        return dto;
    }
}
