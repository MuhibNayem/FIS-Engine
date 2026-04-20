package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Bill;
import com.bracit.fisprocess.domain.entity.BillLine;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Vendor;
import com.bracit.fisprocess.domain.enums.BillStatus;
import com.bracit.fisprocess.dto.request.BillLineRequestDto;
import com.bracit.fisprocess.dto.request.CreateBillRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.CreateVendorRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.response.BillResponseDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
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
import com.bracit.fisprocess.service.BillService;
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
 * Implementation of {@link BillService} for AP Bill and Vendor operations.
 * <p>
 * Enforces all business rules including GL journal posting on finalization,
 * tenant scoping, and bill validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BillServiceImpl implements BillService {

    private final BillRepository billRepository;
    private final BillLineRepository billLineRepository;
    private final VendorRepository vendorRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final BillPaymentApplicationRepository billPaymentApplicationRepository;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper modelMapper;

    @Value("${fis.ap.tax-account-code:TAX_RECEIVABLE}")
    private String taxAccountCode;

    @Override
    @Transactional
    public Vendor createVendor(UUID tenantId, CreateVendorRequestDto request) {
        validateTenantExists(tenantId);
        validateVendorCodeUniqueness(tenantId, request.getCode());

        Vendor vendor = Vendor.builder()
                .tenantId(tenantId)
                .code(request.getCode().trim())
                .name(request.getName().trim())
                .taxId(request.getTaxId())
                .currency(request.getCurrency())
                .paymentTerms(request.getPaymentTerms())
                .status(Vendor.VendorStatus.ACTIVE)
                .build();

        Vendor saved = vendorRepository.save(vendor);
        log.info("Created vendor '{}' for tenant '{}'", saved.getCode(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public Bill createBill(UUID tenantId, CreateBillRequestDto request) {
        validateTenantExists(tenantId);
        validateVendorExists(tenantId, request.getVendorId());

        // Generate bill number: BILL-{tenantId-short}-{timestamp}
        String billNumber = generateBillNumber(tenantId);

        Bill bill = Bill.builder()
                .tenantId(tenantId)
                .vendorId(request.getVendorId())
                .billNumber(billNumber)
                .billDate(request.getBillDate())
                .dueDate(request.getDueDate())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .referenceId(request.getReferenceId())
                .status(BillStatus.DRAFT)
                .build();

        // Build lines and calculate totals
        long subtotalAmount = 0L;
        long taxAmount = 0L;
        int sortIdx = 0;

        for (BillLineRequestDto lineDto : request.getLines()) {
            long lineSubtotal = lineDto.getQuantity() * lineDto.getUnitPrice();
            long lineTax = BigDecimalMath.multiplyBasisPoints(lineSubtotal, lineDto.getTaxRate());
            long lineTotal = lineSubtotal + lineTax;

            BillLine line = BillLine.builder()
                    .description(lineDto.getDescription())
                    .quantity(lineDto.getQuantity())
                    .unitPrice(lineDto.getUnitPrice())
                    .taxRate(lineDto.getTaxRate())
                    .lineTotal(lineTotal)
                    .glAccountId(lineDto.getGlAccountId())
                    .sortOrder(lineDto.getSortOrder() != null ? lineDto.getSortOrder() : sortIdx++)
                    .build();

            bill.addLine(line);
            subtotalAmount += lineSubtotal;
            taxAmount += lineTax;
        }

        bill.setSubtotalAmount(subtotalAmount);
        bill.setTaxAmount(taxAmount);
        bill.setTotalAmount(subtotalAmount + taxAmount);

        Bill saved = billRepository.save(bill);
        log.info("Created draft bill '{}' for tenant '{}'", saved.getBillNumber(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public Bill finalizeBill(UUID tenantId, UUID billId, String performedBy) {
        Bill bill = getBillOrThrow(tenantId, billId);

        // BR3: Cannot finalize already-finalized bill
        if (bill.getStatus() != BillStatus.DRAFT) {
            throw new BillAlreadyFinalizedException(billId);
        }

        // BR2: Cannot finalize with no lines
        if (bill.getLines().isEmpty()) {
            throw new InvalidBillException(billId, "Bill must have at least one line to finalize");
        }

        // BR1: Validate total equals sum of line totals
        validateBillTotals(bill);

        // FR-11: Validate accounting period is OPEN before posting to GL
        periodValidationService.validatePostingAllowed(tenantId, bill.getBillDate(), null);

        // Post journal to GL
        postBillJournal(bill, performedBy);

        // Transition to POSTED
        bill.setStatus(BillStatus.POSTED);
        Bill updated = billRepository.save(bill);

        log.info("Finalized bill '{}' for tenant '{}' — GL journal posted", billId, tenantId);
        return updated;
    }

    @Override
    @Transactional
    public Bill voidBill(UUID tenantId, UUID billId, String performedBy) {
        Bill bill = getBillOrThrow(tenantId, billId);

        // BR4: Cannot void a finalized bill that has payments applied
        if (bill.getStatus() != BillStatus.DRAFT) {
            List<com.bracit.fisprocess.domain.entity.BillPaymentApplication> applications =
                    billPaymentApplicationRepository.findByBillId(billId);
            if (!applications.isEmpty()) {
                throw new InvalidBillException(billId,
                        "Cannot void bill with " + applications.size() + " payment(s) applied");
            }
            throw new InvalidBillException(billId,
                    "Cannot void a non-draft bill. Use a Debit Note for corrections.");
        }

        bill.setStatus(BillStatus.OVERDUE);
        Bill updated = billRepository.save(bill);

        log.info("Voided bill '{}' for tenant '{}'", billId, tenantId);
        return updated;
    }

    @Override
    public Bill getBill(UUID tenantId, UUID billId) {
        return getBillOrThrow(tenantId, billId);
    }

    @Override
    public Vendor getVendor(UUID tenantId, UUID vendorId) {
        return vendorRepository.findById(vendorId)
                .filter(v -> v.getTenantId().equals(tenantId))
                .orElseThrow(() -> new VendorNotFoundException(vendorId));
    }

    @Override
    public Page<Vendor> listVendors(
            UUID tenantId,
            @Nullable String search,
            Pageable pageable) {
        validateTenantExists(tenantId);
        return vendorRepository.findByTenantIdWithFilters(tenantId, search, pageable);
    }

    @Override
    public Page<BillResponseDto> listBills(
            UUID tenantId,
            @Nullable UUID vendorId,
            @Nullable BillStatus status,
            Pageable pageable) {
        validateTenantExists(tenantId);

        return billRepository.findByTenantIdWithFilters(tenantId, vendorId, status, pageable)
                .map(this::toResponseDto);
    }

    @Override
    public List<BillLine> getBillLines(UUID billId) {
        return billLineRepository.findByBillIdOrderBySortOrder(billId);
    }

    // --- Private Helper Methods ---

    private Bill getBillOrThrow(UUID tenantId, UUID billId) {
        return billRepository.findByTenantIdAndId(tenantId, billId)
                .orElseThrow(() -> new BillNotFoundException(billId));
    }

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private void validateVendorCodeUniqueness(UUID tenantId, String code) {
        if (vendorRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException(
                    "Vendor code '" + code + "' already exists for this tenant");
        }
    }

    private void validateVendorExists(UUID tenantId, UUID vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new VendorNotFoundException(vendorId));
        // Verify tenant ownership
        if (!vendor.getTenantId().equals(tenantId)) {
            throw new VendorNotFoundException(vendorId);
        }
    }

    private String generateBillNumber(UUID tenantId) {
        String shortTenant = tenantId.toString().substring(0, 8).toUpperCase();
        return "BILL-" + shortTenant + "-" + System.currentTimeMillis();
    }

    /**
     * BR1: Validates that bill total equals sum of line totals (including tax).
     */
    private void validateBillTotals(Bill bill) {
        long computedSubtotal = 0L;
        long computedTax = 0L;
        long computedTotal = 0L;

        for (BillLine line : bill.getLines()) {
            long lineSubtotal = line.getQuantity() * line.getUnitPrice();
            long lineTax = BigDecimalMath.multiplyBasisPoints(lineSubtotal, line.getTaxRate());
            computedSubtotal += lineSubtotal;
            computedTax += lineTax;
            computedTotal += line.getLineTotal();
        }

        long expectedTotal = computedSubtotal + computedTax;
        if (Math.abs(bill.getTotalAmount() - expectedTotal) > 1) {
            throw new InvalidBillException(bill.getBillId(),
                    "Total amount mismatch: expected " + expectedTotal + ", got " + bill.getTotalAmount());
        }
    }

    /**
     * Posts the journal entry for bill finalization.
     * <pre>
     * DEBIT:  Expense (per line glAccountId)  = subtotalAmount
     * DEBIT:  Tax Receivable                  = taxAmount
     * CREDIT: Accounts Payable                = totalAmount
     * </pre>
     */
    private void postBillJournal(Bill bill, String performedBy) {
        try {
            List<JournalLineRequestDto> journalLines = new ArrayList<>();

            // DEBIT: Expense accounts per line
            for (BillLine line : bill.getLines()) {
                long lineSubtotal = line.getQuantity() * line.getUnitPrice();
                String expenseAccountCode = line.getGlAccountId() != null
                        ? line.getGlAccountId().toString().substring(0, 8).toUpperCase()
                        : "EXPENSE";
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(expenseAccountCode)
                        .amountCents(lineSubtotal)
                        .isCredit(false)
                        .build());
            }

            // DEBIT: Tax Receivable = taxAmount
            if (bill.getTaxAmount() > 0) {
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(taxAccountCode)
                        .amountCents(bill.getTaxAmount())
                        .isCredit(false)
                        .build());
            }

            // CREDIT: Accounts Payable = totalAmount
            journalLines.add(JournalLineRequestDto.builder()
                    .accountCode("AP-" + bill.getVendorId().toString().substring(0, 8).toUpperCase())
                    .amountCents(bill.getTotalAmount())
                    .isCredit(true)
                    .build());

            String eventId = "AP-" + bill.getBillId() + "-FINALIZE";

            journalEntryService.createJournalEntry(
                    bill.getTenantId(),
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(bill.getBillDate())
                            .transactionDate(bill.getBillDate())
                            .description("Bill " + bill.getBillNumber() + " finalization")
                            .referenceId("BILL-" + bill.getBillId())
                            .transactionCurrency(bill.getCurrency())
                            .createdBy(performedBy)
                            .lines(journalLines)
                            .build());

        } catch (Exception ex) {
            log.error("Failed to post GL journal for bill '{}': {}", bill.getBillId(), ex.getMessage(), ex);
            throw new InvalidBillException(bill.getBillId(),
                    "Failed to post journal entry to GL: " + ex.getMessage());
        }
    }

    private BillResponseDto toResponseDto(Bill bill) {
        BillResponseDto dto = modelMapper.map(bill, BillResponseDto.class);
        dto.setBillId(bill.getBillId());
        dto.setOutstandingAmount(bill.getOutstandingAmount());
        dto.setBillDate(bill.getBillDate().toString());
        dto.setDueDate(bill.getDueDate().toString());
        dto.setStatus(bill.getStatus().name());

        if (bill.getLines() != null && !bill.getLines().isEmpty()) {
            dto.setLines(bill.getLines().stream()
                    .map(this::toLineResponseDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private com.bracit.fisprocess.dto.response.BillLineResponseDto toLineResponseDto(BillLine line) {
        com.bracit.fisprocess.dto.response.BillLineResponseDto dto =
                modelMapper.map(line, com.bracit.fisprocess.dto.response.BillLineResponseDto.class);
        dto.setBillLineId(line.getBillLineId());
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
