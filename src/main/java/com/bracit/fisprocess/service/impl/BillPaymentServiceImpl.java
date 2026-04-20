package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Bill;
import com.bracit.fisprocess.domain.entity.BillPayment;
import com.bracit.fisprocess.domain.entity.BillPaymentApplication;
import com.bracit.fisprocess.domain.entity.Vendor;
import com.bracit.fisprocess.domain.enums.BillPaymentStatus;
import com.bracit.fisprocess.domain.enums.BillStatus;
import com.bracit.fisprocess.dto.request.ApplyBillPaymentRequestDto;
import com.bracit.fisprocess.dto.request.ApplyBillPaymentRequestDto.BillPaymentApplicationRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.RecordBillPaymentRequestDto;
import com.bracit.fisprocess.dto.response.BillPaymentResponseDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
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
import com.bracit.fisprocess.service.BillPaymentService;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link BillPaymentService} for AP Bill Payment operations.
 * <p>
 * Enforces all business rules including outstanding balance checks,
 * GL journal posting on payment application, and tenant scoping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BillPaymentServiceImpl implements BillPaymentService {

    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final BillPaymentApplicationRepository billPaymentApplicationRepository;
    private final VendorRepository vendorRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper modelMapper;

    @Value("${fis.ap.bank-account-code:BANK}")
    private String bankAccountCode;

    @Override
    @Transactional
    public BillPayment recordPayment(UUID tenantId, RecordBillPaymentRequestDto request, String performedBy) {
        validateTenantExists(tenantId);
        validateVendorExists(tenantId, request.getVendorId());

        BillPayment payment = BillPayment.builder()
                .tenantId(tenantId)
                .vendorId(request.getVendorId())
                .amount(request.getAmount())
                .paymentDate(request.getPaymentDate())
                .method(request.getMethod())
                .reference(request.getReference())
                .status(BillPaymentStatus.PENDING)
                .build();

        BillPayment saved = billPaymentRepository.save(payment);
        log.info("Recorded bill payment '{}' for vendor '{}' tenant '{}'", saved.getBillPaymentId(),
                request.getVendorId(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public BillPayment applyPayment(UUID tenantId, ApplyBillPaymentRequestDto request, String performedBy) {
        // FR-11: Validate accounting period is OPEN before posting to GL
        periodValidationService.validatePostingAllowed(tenantId, LocalDate.now(), null);

        // Load and validate payment
        BillPayment payment = getPaymentOrThrow(tenantId, request.getPaymentId());

        if (payment.getStatus() != BillPaymentStatus.PENDING) {
            throw new InvalidBillException(payment.getBillPaymentId(),
                    "Payment is not in PENDING status — current status: " + payment.getStatus());
        }

        // Validate each application
        long totalApplied = 0L;
        List<BillPaymentApplication> applications = new ArrayList<>();

        for (BillPaymentApplicationRequestDto appDto : request.getApplications()) {
            Bill bill = billRepository.findByTenantIdAndId(tenantId, appDto.getBillId())
                    .orElseThrow(() -> new BillNotFoundException(appDto.getBillId()));

            // BR6: Cannot apply to non-POSTED bill
            if (bill.getStatus() != BillStatus.POSTED
                    && bill.getStatus() != BillStatus.PARTIALLY_PAID) {
                throw new InvalidBillException(bill.getBillId(),
                        "Cannot apply payment to bill in status: " + bill.getStatus());
            }

            // BR5: Payment cannot exceed outstanding balance
            Long outstanding = bill.getOutstandingAmount();
            if (appDto.getAppliedAmount() > outstanding) {
                throw new BillPaymentExceedsOutstandingException(
                        "Applied amount " + appDto.getAppliedAmount()
                                + " exceeds outstanding balance " + outstanding
                                + " for bill " + bill.getBillId());
            }

            totalApplied += appDto.getAppliedAmount();

            // Create application
            BillPaymentApplication application = BillPaymentApplication.builder()
                    .payment(payment)
                    .billId(bill.getBillId())
                    .appliedAmount(appDto.getAppliedAmount())
                    .build();

            applications.add(application);

            // Post GL journal for this application
            postPaymentApplicationJournal(bill, appDto.getAppliedAmount(), performedBy, tenantId);

            // Update bill paid amount
            bill.setPaidAmount(bill.getPaidAmount() + appDto.getAppliedAmount());

            // Update bill status
            if (bill.getOutstandingAmount() <= 0) {
                bill.setStatus(BillStatus.PAID);
            } else {
                bill.setStatus(BillStatus.PARTIALLY_PAID);
            }

            billRepository.save(bill);
        }

        // BR5 (aggregate): total applied cannot exceed payment amount
        if (totalApplied > payment.getAmount()) {
            throw new BillPaymentExceedsOutstandingException(
                    "Total applied amount " + totalApplied
                            + " exceeds payment amount " + payment.getAmount());
        }

        // Save applications
        payment.getApplications().addAll(applications);
        payment.setStatus(BillPaymentStatus.APPLIED);
        BillPayment updated = billPaymentRepository.save(payment);

        log.info("Applied payment '{}' for tenant '{}' — {} application(s)", payment.getBillPaymentId(), tenantId,
                applications.size());
        return updated;
    }

    @Override
    public BillPayment getPayment(UUID tenantId, UUID paymentId) {
        return getPaymentOrThrow(tenantId, paymentId);
    }

    @Override
    public List<BillPaymentApplication> getPaymentApplications(UUID paymentId) {
        return billPaymentApplicationRepository.findByPaymentId(paymentId);
    }

    @Override
    public Page<BillPaymentResponseDto> listPayments(
            UUID tenantId,
            @Nullable UUID vendorId,
            Pageable pageable) {
        validateTenantExists(tenantId);

        return billPaymentRepository.findByTenantIdWithFilters(tenantId, vendorId, null, pageable)
                .map(this::toResponseDto);
    }

    // --- Private Helper Methods ---

    private BillPayment getPaymentOrThrow(UUID tenantId, UUID paymentId) {
        return billPaymentRepository.findById(paymentId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BillNotFoundException(paymentId));
    }

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private void validateVendorExists(UUID tenantId, UUID vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new VendorNotFoundException(vendorId));
        if (!vendor.getTenantId().equals(tenantId)) {
            throw new VendorNotFoundException(vendorId);
        }
    }

    /**
     * Posts the journal entry for a payment application.
     * <pre>
     * DEBIT:  Accounts Payable  = appliedAmount
     * CREDIT: Bank Account      = appliedAmount
     * </pre>
     */
    private void postPaymentApplicationJournal(
            Bill bill, Long appliedAmount, String performedBy, UUID tenantId) {
        try {
            String eventId = "AP-PAY-" + bill.getBillId() + "-" + System.nanoTime();

            journalEntryService.createJournalEntry(
                    tenantId,
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(LocalDate.now())
                            .transactionDate(LocalDate.now())
                            .description("Payment application against bill " + bill.getBillNumber())
                            .referenceId("PAY-BILL-" + bill.getBillId())
                            .transactionCurrency(bill.getCurrency())
                            .createdBy(performedBy)
                            .lines(List.of(
                                    JournalLineRequestDto.builder()
                                            .accountCode("AP-" + bill.getVendorId().toString()
                                                    .substring(0, 8).toUpperCase())
                                            .amountCents(appliedAmount)
                                            .isCredit(false)
                                            .build(),
                                    JournalLineRequestDto.builder()
                                            .accountCode(bankAccountCode)
                                            .amountCents(appliedAmount)
                                            .isCredit(true)
                                            .build()))
                            .build());
        } catch (Exception ex) {
            log.error("Failed to post GL journal for payment application on bill '{}': {}",
                    bill.getBillId(), ex.getMessage(), ex);
            throw new InvalidBillException(bill.getBillId(),
                    "Failed to post payment journal to GL: " + ex.getMessage());
        }
    }

    private BillPaymentResponseDto toResponseDto(BillPayment payment) {
        BillPaymentResponseDto dto = modelMapper.map(payment, BillPaymentResponseDto.class);
        dto.setBillPaymentId(payment.getBillPaymentId());
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

    private com.bracit.fisprocess.dto.response.BillPaymentApplicationResponseDto toApplicationResponseDto(
            BillPaymentApplication app) {
        com.bracit.fisprocess.dto.response.BillPaymentApplicationResponseDto dto =
                modelMapper.map(app, com.bracit.fisprocess.dto.response.BillPaymentApplicationResponseDto.class);
        dto.setApplicationId(app.getApplicationId());

        // Resolve bill number
        billRepository.findById(app.getBillId())
                .ifPresent(bill -> dto.setBillNumber(bill.getBillNumber()));

        return dto;
    }
}
