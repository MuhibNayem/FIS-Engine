package com.bracit.fisprocess.domain.entity;

import com.bracit.fisprocess.domain.enums.PeriodStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant-scoped fiscal period boundary and status.
 */
@Entity
@Table(name = "fis_accounting_period", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "name" }))
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPeriod {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "period_id", nullable = false, updatable = false)
    private UUID periodId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodStatus status;

    @Nullable
    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Nullable
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (periodId == null) {
            periodId = UUID.randomUUID();
        }
        if (status == null) {
            status = PeriodStatus.OPEN;
        }
    }
}
