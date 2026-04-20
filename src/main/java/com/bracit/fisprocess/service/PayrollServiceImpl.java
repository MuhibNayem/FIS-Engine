package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Employee;
import com.bracit.fisprocess.domain.entity.PayrollLine;
import com.bracit.fisprocess.domain.entity.PayrollRun;
import com.bracit.fisprocess.domain.enums.PayrollRunStatus;
import com.bracit.fisprocess.dto.request.CreatePayrollRunRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.RegisterEmployeeRequestDto;
import com.bracit.fisprocess.dto.response.EmployeeResponseDto;
import com.bracit.fisprocess.dto.response.PayrollRunResponseDto;
import com.bracit.fisprocess.repository.EmployeeRepository;
import com.bracit.fisprocess.repository.PayrollLineRepository;
import com.bracit.fisprocess.repository.PayrollRunRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PayrollService;
import com.bracit.fisprocess.service.PeriodValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PayrollServiceImpl implements PayrollService {

    private final EmployeeRepository empRepo;
    private final PayrollRunRepository runRepo;
    private final PayrollLineRepository lineRepo;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper mapper;

    @Value("${fis.payroll.salary-expense-account:SALARY_EXPENSE}")
    private String salaryExpenseAccount;

    @Value("${fis.payroll.income-tax-payable:INCOME_TAX_PAYABLE}")
    private String incomeTaxPayableAccount;

    @Value("${fis.payroll.social-security-payable:SOCIAL_SECURITY_PAYABLE}")
    private String socialSecurityPayableAccount;

    @Value("${fis.payroll.benefits-expense:BENEFITS_EXPENSE}")
    private String benefitsExpenseAccount;

    @Value("${fis.payroll.employer-benefits-payable:EMPLOYER_BENEFITS_PAYABLE}")
    private String employerBenefitsPayableAccount;

    @Override
    @Transactional
    public EmployeeResponseDto registerEmployee(UUID tenantId, RegisterEmployeeRequestDto req) {
        var emp = mapper.map(req, Employee.class);
        emp.setTenantId(tenantId);
        var saved = empRepo.save(emp);
        log.info("Registered employee '{}' for tenant '{}'", saved.getCode(), tenantId);
        return mapper.map(saved, EmployeeResponseDto.class);
    }

    @Override
    @Transactional
    public PayrollRunResponseDto createRun(UUID tenantId, CreatePayrollRunRequestDto req, String performedBy) {
        var run = PayrollRun.builder()
                .tenantId(tenantId)
                .period(req.getPeriod())
                .runDate(LocalDate.now())
                .totalGross(0L)
                .totalDeductions(0L)
                .totalNet(0L)
                .status(PayrollRunStatus.DRAFT)
                .createdBy(performedBy)
                .build();
        var saved = runRepo.save(run);
        log.info("Created payroll run '{}' for tenant '{}' period '{}'", saved.getId(), tenantId, req.getPeriod());
        return mapper.map(saved, PayrollRunResponseDto.class);
    }

    @Override
    @Transactional
    public PayrollRunResponseDto calculateAndPost(UUID tenantId, UUID runId, String performedBy) {
        // FR-11: Validate accounting period is OPEN before posting to GL
        periodValidationService.validatePostingAllowed(tenantId, LocalDate.now(), null);

        var run = runRepo.findByTenantIdAndId(tenantId, runId)
                .orElseThrow(() -> new RuntimeException("Payroll run not found: " + runId));

        if (run.getStatus() == PayrollRunStatus.POSTED) {
            throw new RuntimeException("Payroll run already posted");
        }

        var employees = empRepo.findAll().stream()
                .filter(e -> e.getTenantId().equals(tenantId))
                .toList();

        long totalGross = 0;
        long totalDeductions = 0;
        long totalNet = 0;

        for (var emp : employees) {
            long grossSalary = emp.getBasicSalary() != null ? emp.getBasicSalary() : 0L;
            long allowances = emp.getAllowances() != null ? emp.getAllowances() : 0L;
            long grossTotal = grossSalary + allowances;

            long taxableIncome = grossTotal;
            long incomeTax = calculateIncomeTax(taxableIncome);
            long socialSecurity = calculateSocialSecurity(grossSalary);
            long otherDeductions = 0;
            long netPay = grossTotal - incomeTax - socialSecurity - otherDeductions;

            var line = PayrollLine.builder()
                    .tenantId(tenantId)
                    .runId(runId)
                    .employeeId(emp.getId())
                    .grossSalary(grossTotal)
                    .allowances(allowances)
                    .taxableIncome(taxableIncome)
                    .incomeTax(incomeTax)
                    .socialSecurity(socialSecurity)
                    .netPay(netPay)
                    .build();
            lineRepo.save(line);

            totalGross += grossTotal;
            totalDeductions += incomeTax + socialSecurity;
            totalNet += netPay;
        }

        run.setTotalGross(totalGross);
        run.setTotalDeductions(totalDeductions);
        run.setTotalNet(totalNet);
        run.setStatus(PayrollRunStatus.POSTED);
        run.setApprovedBy(performedBy);
        runRepo.save(run);

        postPayrollJournal(tenantId, run, totalGross, totalDeductions, totalNet, performedBy);

        log.info("Calculated and posted payroll run '{}' for tenant '{}' — gross: {}, net: {}",
                runId, tenantId, totalGross, totalNet);
        return mapper.map(run, PayrollRunResponseDto.class);
    }

    @Override
    public PayrollRunResponseDto getById(UUID tenantId, UUID id) {
        return runRepo.findByTenantIdAndId(tenantId, id)
                .map(r -> mapper.map(r, PayrollRunResponseDto.class))
                .orElseThrow(() -> new RuntimeException("Payroll run not found: " + id));
    }

    @Override
    public Page<EmployeeResponseDto> listEmployees(UUID tenantId, Pageable pageable) {
        return empRepo.findByTenantId(tenantId, pageable)
                .map(e -> mapper.map(e, EmployeeResponseDto.class));
    }

    private long calculateIncomeTax(long taxableIncome) {
        // Simplified progressive tax calculation
        // In production, this would use configurable tax brackets
        if (taxableIncome <= 50000) return 0;
        if (taxableIncome <= 100000) return (taxableIncome - 50000) * 10 / 100;
        if (taxableIncome <= 200000) return 5000 + (taxableIncome - 100000) * 20 / 100;
        return 25000 + (taxableIncome - 200000) * 30 / 100;
    }

    private long calculateSocialSecurity(long grossSalary) {
        // Simplified: 5% of gross, capped at 500
        long ss = grossSalary * 5 / 100;
        return ss > 500 ? 500 : ss;
    }

    private void postPayrollJournal(UUID tenantId, PayrollRun run,
            long totalGross, long totalDeductions, long totalNet,
            String performedBy) {
        try {
            String eventId = "PR-" + run.getId() + "-POST";

            // Get actual tax amounts from PayrollLine records (not approximations)
            Long incomeTaxSum = lineRepo.sumIncomeTaxByRunId(run.getId());
            Long socialSecuritySum = lineRepo.sumSocialSecurityByRunId(run.getId());
            if (incomeTaxSum == null) incomeTaxSum = 0L;
            if (socialSecuritySum == null) socialSecuritySum = 0L;

            List<JournalLineRequestDto> journalLines = new ArrayList<>();

            // DEBIT: Salary Expense (gross)
            journalLines.add(JournalLineRequestDto.builder()
                    .accountCode(salaryExpenseAccount)
                    .amountCents(totalGross)
                    .isCredit(false)
                    .build());

            // DEBIT: Benefits Expense (employer contributions)
            long employerContributions = totalGross * 10 / 100;
            if (employerContributions > 0) {
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(benefitsExpenseAccount)
                        .amountCents(employerContributions)
                        .isCredit(false)
                        .build());
            }

            // CREDIT: Income Tax Payable (actual from payroll lines)
            if (incomeTaxSum > 0) {
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(incomeTaxPayableAccount)
                        .amountCents(incomeTaxSum)
                        .isCredit(true)
                        .build());
            }

            // CREDIT: Social Security Payable (actual from payroll lines)
            if (socialSecuritySum > 0) {
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(socialSecurityPayableAccount)
                        .amountCents(socialSecuritySum)
                        .isCredit(true)
                        .build());
            }

            // CREDIT: Employer Benefits Payable (equals employer contribution debit - balances the journal)
            if (employerContributions > 0) {
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(employerBenefitsPayableAccount)
                        .amountCents(employerContributions)
                        .isCredit(true)
                        .build());
            }

            // CREDIT: Salary Payable (net)
            journalLines.add(JournalLineRequestDto.builder()
                    .accountCode("SALARY_PAYABLE")
                    .amountCents(totalNet)
                    .isCredit(true)
                    .build());

            journalEntryService.createJournalEntry(
                    tenantId,
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(run.getRunDate())
                            .transactionDate(run.getRunDate())
                            .description("Payroll run for period " + run.getPeriod() + " — gross: " + totalGross)
                            .referenceId("PR-" + run.getId())
                            .transactionCurrency("USD")
                            .createdBy(performedBy)
                            .lines(journalLines)
                            .build());

            log.info("Posted payroll journal for run '{}', gross: {}, net: {}",
                    run.getId(), totalGross, totalNet);
        } catch (Exception ex) {
            log.error("Failed to post GL journal for payroll run '{}': {}", run.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to post payroll journal to GL: " + ex.getMessage());
        }
    }
}