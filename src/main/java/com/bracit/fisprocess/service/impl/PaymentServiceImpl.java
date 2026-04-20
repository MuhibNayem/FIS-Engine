package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.ARPayment;
import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.entity.PaymentApplication;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.domain.enums.PaymentStatus;
import com.bracit.fisprocess.dto.request.ApplyPaymentRequestDto;
import com.bracit.fisprocess.dto.request.ApplyPaymentRequestDto.PaymentApplicationRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.RecordPaymentRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.dto.response.PaymentApplicationResponseDto;
import com.bracit.fisprocess.dto.response.PaymentResponseDto;
import com.bracit.fisprocess.exception.CustomerNotFoundException;
import com.bracit.fisprocess.exception.InvoiceNotFoundException;
import com.bracit.fisprocess.exception.InvalidInvoiceException;
import com.bracit.fisprocess.exception.PaymentExceedsOutstandingException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.ARPaymentRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.CustomerRepository;
import com.bracit.fisprocess.repository.InvoiceRepository;
import com.bracit.fisprocess.repository.PaymentApplicationRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PaymentService;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link PaymentService} for AR Payment operations.
 * <p>
 * Enforces all business rules including outstanding balance checks,
 * GL journal posting on payment application, and tenant scoping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final ARPaymentRepository arPaymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentApplicationRepository paymentApplicationRepository;
    private final CustomerRepository customerRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper modelMapper;

    @Value("${fis.ar.bank-account-code:BANK}")
    private String bankAccountCode;

    @Override
    @Transactional
    public ARPayment recordPayment(UUID tenantId, RecordPaymentRequestDto request, String performedBy) {
        validateTenantExists(tenantId);
        validateCustomerExists(tenantId, request.getCustomerId());

        ARPayment payment = ARPayment.builder()
                .tenantId(tenantId)
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .paymentDate(request.getPaymentDate())
                .method(request.getMethod())
                .reference(request.getReference())
                .status(PaymentStatus.PENDING)
                .build();

        ARPayment saved = arPaymentRepository.save(payment);
        log.info("Recorded payment '{}' for customer '{}' tenant '{}'", saved.getPaymentId(),
                request.getCustomerId(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public ARPayment applyPayment(UUID tenantId, ApplyPaymentRequestDto request, String performedBy) {
        // FR-11: Validate accounting period is OPEN before posting to GL
        periodValidationService.validatePostingAllowed(tenantId, LocalDate.now(), null);

        // Load and validate payment
        ARPayment payment = getPaymentOrThrow(tenantId, request.getPaymentId());

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidInvoiceException(payment.getPaymentId(),
                    "Payment is not in PENDING status — current status: " + payment.getStatus());
        }

        // Validate each application
        long totalApplied = 0L;
        List<PaymentApplication> applications = new ArrayList<>();

        for (PaymentApplicationRequestDto appDto : request.getApplications()) {
            Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, appDto.getInvoiceId())
                    .orElseThrow(() -> new InvoiceNotFoundException(appDto.getInvoiceId()));

            // BR6: Cannot apply to non-POSTED invoice
            if (invoice.getStatus() != InvoiceStatus.POSTED
                    && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
                throw new InvalidInvoiceException(invoice.getInvoiceId(),
                        "Cannot apply payment to invoice in status: " + invoice.getStatus());
            }

            // BR5: Payment cannot exceed outstanding balance
            Long outstanding = invoice.getOutstandingAmount();
            if (appDto.getAppliedAmount() > outstanding) {
                throw new PaymentExceedsOutstandingException(
                        "Applied amount " + appDto.getAppliedAmount()
                                + " exceeds outstanding balance " + outstanding
                                + " for invoice " + invoice.getInvoiceId());
            }

            totalApplied += appDto.getAppliedAmount();

            // Create application
            PaymentApplication application = PaymentApplication.builder()
                    .payment(payment)
                    .invoiceId(invoice.getInvoiceId())
                    .appliedAmount(appDto.getAppliedAmount())
                    .build();

            applications.add(application);

            // Post GL journal for this application
            postPaymentApplicationJournal(invoice, appDto.getAppliedAmount(), performedBy, tenantId);

            // Update invoice paid amount
            invoice.setPaidAmount(invoice.getPaidAmount() + appDto.getAppliedAmount());

            // Update invoice status
            if (invoice.getOutstandingAmount() <= 0) {
                invoice.setStatus(InvoiceStatus.PAID);
            } else {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            }

            invoiceRepository.save(invoice);
        }

        // BR5 (aggregate): total applied cannot exceed payment amount
        if (totalApplied > payment.getAmount()) {
            throw new PaymentExceedsOutstandingException(
                    "Total applied amount " + totalApplied
                            + " exceeds payment amount " + payment.getAmount());
        }

        // Save applications
        payment.getApplications().addAll(applications);
        payment.setStatus(PaymentStatus.APPLIED);
        ARPayment updated = arPaymentRepository.save(payment);

        log.info("Applied payment '{}' for tenant '{}' — {} application(s)", payment.getPaymentId(), tenantId,
                applications.size());
        return updated;
    }

    @Override
    public ARPayment getPayment(UUID tenantId, UUID paymentId) {
        return getPaymentOrThrow(tenantId, paymentId);
    }

    @Override
    public List<PaymentApplication> getPaymentApplications(UUID paymentId) {
        return paymentApplicationRepository.findByPaymentId(paymentId);
    }

    @Override
    public Page<PaymentResponseDto> listPayments(
            UUID tenantId,
            @Nullable UUID customerId,
            Pageable pageable) {
        validateTenantExists(tenantId);

        return arPaymentRepository.findByTenantIdWithFilters(tenantId, customerId, null, pageable)
                .map(this::toResponseDto);
    }

    // --- Private Helper Methods ---

    private ARPayment getPaymentOrThrow(UUID tenantId, UUID paymentId) {
        return arPaymentRepository.findById(paymentId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new com.bracit.fisprocess.exception.InvoiceNotFoundException(paymentId));
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
     * Posts the journal entry for a payment application.
     * <pre>
     * DEBIT:  Bank Account    = appliedAmount
     * CREDIT: Accounts Receivable = appliedAmount
     * </pre>
     */
    private void postPaymentApplicationJournal(
            Invoice invoice, Long appliedAmount, String performedBy, UUID tenantId) {
        try {
            String eventId = "AR-PAY-" + invoice.getInvoiceId() + "-" + System.nanoTime();

            journalEntryService.createJournalEntry(
                    tenantId,
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(LocalDate.now())
                            .transactionDate(LocalDate.now())
                            .description("Payment application against invoice " + invoice.getInvoiceNumber())
                            .referenceId("PAY-INV-" + invoice.getInvoiceId())
                            .transactionCurrency(invoice.getCurrency())
                            .createdBy(performedBy)
                            .lines(List.of(
                                    JournalLineRequestDto.builder()
                                            .accountCode(bankAccountCode)
                                            .amountCents(appliedAmount)
                                            .isCredit(false)
                                            .build(),
                                    JournalLineRequestDto.builder()
                                            .accountCode("AR-" + invoice.getCustomerId().toString()
                                                    .substring(0, 8).toUpperCase())
                                            .amountCents(appliedAmount)
                                            .isCredit(true)
                                            .build()))
                            .build());
        } catch (Exception ex) {
            log.error("Failed to post GL journal for payment application on invoice '{}': {}",
                    invoice.getInvoiceId(), ex.getMessage(), ex);
            throw new InvalidInvoiceException(invoice.getInvoiceId(),
                    "Failed to post payment journal to GL: " + ex.getMessage());
        }
    }

    private PaymentResponseDto toResponseDto(ARPayment payment) {
        PaymentResponseDto dto = modelMapper.map(payment, PaymentResponseDto.class);
        dto.setPaymentId(payment.getPaymentId());
        dto.setPaymentDate(payment.getPaymentDate().toString());
        dto.setMethod(payment.getMethod().name());
        dto.setStatus(payment.getStatus().name());

        if (payment.getApplications() != null && !payment.getApplications().isEmpty()) {
            dto.setApplications(payment.getApplications().stream()
                    .map(this::toApplicationResponseDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private PaymentApplicationResponseDto toApplicationResponseDto(PaymentApplication app) {
        PaymentApplicationResponseDto dto = modelMapper.map(app, PaymentApplicationResponseDto.class);
        dto.setApplicationId(app.getApplicationId());

        // Resolve invoice number
        invoiceRepository.findById(app.getInvoiceId())
                .ifPresent(inv -> dto.setInvoiceNumber(inv.getInvoiceNumber()));

        return dto;
    }
}
