package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fis_payrollline")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollLine {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "gross_salary", nullable = false)
    private Long grossSalary;

    @Column(name = "allowances")
    @Builder.Default
    private Long allowances = 0L;

    @Column(name = "deductions")
    @Builder.Default
    private Long deductions = 0L;

    @Column(name = "taxable_income", nullable = false)
    private Long taxableIncome;

    @Column(name = "income_tax")
    @Builder.Default
    private Long incomeTax = 0L;

    @Column(name = "social_security")
    @Builder.Default
    private Long socialSecurity = 0L;

    @Column(name = "net_pay", nullable = false)
    private Long netPay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}