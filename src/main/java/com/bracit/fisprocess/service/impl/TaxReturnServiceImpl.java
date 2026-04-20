package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.TaxJurisdiction;
import com.bracit.fisprocess.domain.entity.TaxReturn;
import com.bracit.fisprocess.domain.entity.TaxReturnLine;
import com.bracit.fisprocess.domain.enums.TaxDirection;
import com.bracit.fisprocess.domain.enums.TaxReturnStatus;
import com.bracit.fisprocess.dto.request.GenerateTaxReturnRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.response.TaxLiabilityReportDto;
import com.bracit.fisprocess.dto.response.TaxLiabilityReportDto.TaxLiabilityLineDto;
import com.bracit.fisprocess.dto.response.TaxReturnLineResponseDto;
import com.bracit.fisprocess.dto.response.TaxReturnResponseDto;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.exception.TaxReturnAlreadyFiledException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.TaxJurisdictionRepository;
import com.bracit.fisprocess.repository.TaxRateRepository;
import com.bracit.fisprocess.repository.TaxReturnLineRepository;
import com.bracit.fisprocess.repository.TaxReturnRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PeriodValidationService;
import com.bracit.fisprocess.service.TaxReturnService;
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
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link TaxReturnService} for Tax Return operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaxReturnServiceImpl implements TaxReturnService {

    private final TaxReturnRepository taxReturnRepository;
    private final TaxReturnLineRepository taxReturnLineRepository;
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final TaxRateRepository taxRateRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper modelMapper;

    @Value("${fis.tax.tax-payable-account:TAX_PAYABLE}")
    private String taxPayableAccount;

    @Value("${fis.tax.tax-receivable-account:TAX_RECEIVABLE}")
    private String taxReceivableAccount;

    @Override
    @Transactional
    public TaxReturn generate(UUID tenantId, GenerateTaxReturnRequestDto request, String performedBy) {
        validateTenantExists(tenantId);

        // Validate jurisdiction exists and belongs to tenant
        TaxJurisdiction jurisdiction = taxJurisdictionRepository.findById(request.getJurisdictionId())
                .filter(j -> j.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tax jurisdiction '" + request.getJurisdictionId() + "' not found for tenant"));

        // Check if return already exists for this period
        taxReturnRepository.findByTenantIdAndJurisdictionIdAndPeriod(
                        tenantId, request.getJurisdictionId(), request.getPeriod())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Tax return already exists for jurisdiction '" + request.getJurisdictionId()
                                    + "' and period '" + request.getPeriod() + "'");
                });

        TaxReturn taxReturn = TaxReturn.builder()
                .tenantId(tenantId)
                .jurisdictionId(request.getJurisdictionId())
                .period(request.getPeriod())
                .totalOutputTax(0L)
                .totalInputTax(0L)
                .netPayable(0L)
                .status(TaxReturnStatus.DRAFT)
                .build();

        TaxReturn saved = taxReturnRepository.save(taxReturn);
        log.info("Generated tax return '{}' for tenant '{}', jurisdiction '{}'",
                saved.getTaxReturnId(), tenantId, request.getJurisdictionId());
        return saved;
    }

    @Override
    @Transactional
    public TaxReturn file(UUID tenantId, UUID taxReturnId, String performedBy) {
        TaxReturn taxReturn = getTaxReturnOrThrow(tenantId, taxReturnId);

        if (taxReturn.getStatus() != TaxReturnStatus.DRAFT) {
            throw new TaxReturnAlreadyFiledException(taxReturnId);
        }

        // FR-11: Validate accounting period is OPEN before posting to GL
        periodValidationService.validatePostingAllowed(tenantId, LocalDate.now(), null);

        // Post tax liability/receivable journal to GL
        postTaxPaymentJournal(taxReturn, performedBy);

        // Transition to FILED
        taxReturn.setStatus(TaxReturnStatus.FILED);
        taxReturn.setFiledAt(OffsetDateTime.now());

        TaxReturn updated = taxReturnRepository.save(taxReturn);

        log.info("Filed tax return '{}' for tenant '{}' — tax liability journal posted", taxReturnId, tenantId);
        return updated;
    }

    @Override
    public TaxReturn getById(UUID tenantId, UUID taxReturnId) {
        return getTaxReturnOrThrow(tenantId, taxReturnId);
    }

    @Override
    public Page<TaxReturnResponseDto> list(
            UUID tenantId,
            @Nullable UUID jurisdictionId,
            @Nullable TaxReturnStatus status,
            Pageable pageable) {
        validateTenantExists(tenantId);

        return taxReturnRepository.findByTenantIdWithFilters(tenantId, jurisdictionId, status, pageable)
                .map(this::toResponseDto);
    }

    @Override
    public TaxLiabilityReportDto getLiabilityReport(
            UUID tenantId,
            UUID jurisdictionId,
            LocalDate fromDate,
            LocalDate toDate) {
        validateTenantExists(tenantId);

        // Validate jurisdiction
        taxJurisdictionRepository.findById(jurisdictionId)
                .filter(j -> j.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tax jurisdiction '" + jurisdictionId + "' not found for tenant"));

        // Fetch filed returns for the jurisdiction within date range
        // In production, this would be a more sophisticated query
        List<TaxReturn> returns = fetchReturnsForPeriod(tenantId, jurisdictionId, fromDate, toDate);

        long totalOutputTax = 0L;
        long totalInputTax = 0L;
        List<TaxLiabilityLineDto> lines = new ArrayList<>();

        for (TaxReturn taxReturn : returns) {
            totalOutputTax += taxReturn.getTotalOutputTax();
            totalInputTax += taxReturn.getTotalInputTax();

            // Aggregate lines
            List<TaxReturnLine> returnLines = taxReturnLineRepository.findByTaxReturnId(taxReturn.getTaxReturnId());
            for (TaxReturnLine line : returnLines) {
                String taxRateCode = taxRateRepository.findById(line.getTaxRateId())
                        .map(r -> r.getCode())
                        .orElse("UNKNOWN");

                lines.add(TaxLiabilityLineDto.builder()
                        .taxRateCode(taxRateCode)
                        .taxableAmount(line.getTaxableAmount())
                        .taxAmount(line.getTaxAmount())
                        .direction(line.getDirection().name())
                        .build());
            }
        }

        return TaxLiabilityReportDto.builder()
                .jurisdictionId(jurisdictionId.toString())
                .fromDate(fromDate.toString())
                .toDate(toDate.toString())
                .totalOutputTax(totalOutputTax)
                .totalInputTax(totalInputTax)
                .netPayable(totalOutputTax - totalInputTax)
                .lines(lines)
                .build();
    }

    // --- Private Helper Methods ---

    private TaxReturn getTaxReturnOrThrow(UUID tenantId, UUID taxReturnId) {
        return taxReturnRepository.findByTenantIdAndId(tenantId, taxReturnId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tax return '" + taxReturnId + "' not found for tenant"));
    }

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private List<TaxReturn> fetchReturnsForPeriod(
            UUID tenantId, UUID jurisdictionId, LocalDate fromDate, LocalDate toDate) {
        YearMonth fromYM = YearMonth.from(fromDate);
        YearMonth toYM = YearMonth.from(toDate);

        List<TaxReturn> allReturns = new ArrayList<>();
        int page = 0;
        int pageSize = 1000;

        while (true) {
            var pageResult = taxReturnRepository.findByTenantIdWithFilters(
                    tenantId, jurisdictionId, null,
                    org.springframework.data.domain.PageRequest.of(page, pageSize));

            for (TaxReturn tr : pageResult.getContent()) {
                YearMonth returnYM = tr.getPeriod();
                if (!returnYM.isBefore(fromYM) && !returnYM.isAfter(toYM)) {
                    allReturns.add(tr);
                }
            }

            if (pageResult.isLast()) {
                break;
            }
            page++;
        }

        return allReturns;
    }

    /**
     * Posts the journal entry for tax payment/filing.
     * <pre>
     * If netPayable > 0 (tax owed):
     *   DEBIT: Tax Payable = netPayable
     *   CREDIT: Bank Account = netPayable
     *
     * If netPayable < 0 (tax refund):
     *   DEBIT: Bank Account = abs(netPayable)
     *   CREDIT: Tax Receivable = abs(netPayable)
     * </pre>
     */
    private void postTaxPaymentJournal(TaxReturn taxReturn, String performedBy) {
        if (taxReturn.getNetPayable() == 0) {
            log.info("Tax return '{}' has zero net payable - no GL journal posted", taxReturn.getTaxReturnId());
            return;
        }

        try {
            String eventId = "TAX-PAY-" + taxReturn.getTaxReturnId();
            long netPayable = taxReturn.getNetPayable();

            List<JournalLineRequestDto> journalLines = new ArrayList<>();

            if (netPayable > 0) {
                // Tax owed - pay from bank
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(taxPayableAccount)
                        .amountCents(netPayable)
                        .isCredit(false)
                        .build());
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode("BANK")
                        .amountCents(netPayable)
                        .isCredit(true)
                        .build());
            } else {
                // Tax refund - receive to bank
                long refund = Math.abs(netPayable);
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode("BANK")
                        .amountCents(refund)
                        .isCredit(false)
                        .build());
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(taxReceivableAccount)
                        .amountCents(refund)
                        .isCredit(true)
                        .build());
            }

            journalEntryService.createJournalEntry(
                    taxReturn.getTenantId(),
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(LocalDate.now())
                            .transactionDate(LocalDate.now())
                            .description("Tax return filing - period " + taxReturn.getPeriod()
                                    + ", net payable: " + taxReturn.getNetPayable())
                            .referenceId("TAX-" + taxReturn.getTaxReturnId())
                            .transactionCurrency("USD")
                            .createdBy(performedBy)
                            .lines(journalLines)
                            .build());

            log.info("Posted tax payment journal for return '{}', net: {}", taxReturn.getTaxReturnId(), netPayable);
        } catch (Exception ex) {
            log.error("Failed to post GL journal for tax return '{}': {}", taxReturn.getTaxReturnId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to post tax payment journal to GL: " + ex.getMessage());
        }
    }

    private TaxReturnResponseDto toResponseDto(TaxReturn taxReturn) {
        TaxReturnResponseDto dto = modelMapper.map(taxReturn, TaxReturnResponseDto.class);
        dto.setTaxReturnId(taxReturn.getTaxReturnId());
        dto.setPeriod(taxReturn.getPeriod().toString());
        dto.setStatus(taxReturn.getStatus().name());

        // Load lines
        List<TaxReturnLine> lines = taxReturnLineRepository.findByTaxReturnId(taxReturn.getTaxReturnId());
        if (!lines.isEmpty()) {
            dto.setLines(lines.stream().map(line -> {
                TaxReturnLineResponseDto lineDto = modelMapper.map(line, TaxReturnLineResponseDto.class);
                lineDto.setTaxReturnLineId(line.getTaxReturnLineId());
                lineDto.setDirection(line.getDirection().name());

                taxRateRepository.findById(line.getTaxRateId())
                        .ifPresent(rate -> lineDto.setTaxRateCode(rate.getCode()));

                return lineDto;
            }).collect(Collectors.toList()));
        }

        return dto;
    }
}
