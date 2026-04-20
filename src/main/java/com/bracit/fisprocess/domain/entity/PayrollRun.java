package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import com.bracit.fisprocess.domain.enums.PayrollRunStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fis_payrollrun")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRun {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "total_gross", nullable = false)
    private Long totalGross;

    @Column(name = "total_deductions", nullable = false)
    private Long totalDeductions;

    @Column(name = "total_net", nullable = false)
    private Long totalNet;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PayrollRunStatus status = PayrollRunStatus.DRAFT;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}