package com.bracit.fisprocess.domain.entity;

import com.bracit.fisprocess.domain.enums.TaxReturnStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Tax Return — represents a filed tax return for a jurisdiction and period.
 */
@Entity
@Table(name = "fis_tax_return")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReturn {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_return_id", updatable = false, nullable = false)
    private UUID taxReturnId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "jurisdiction_id", nullable = false)
    private UUID jurisdictionId;

    @Column(name = "period", nullable = false)
    private YearMonth period;

    @Column(name = "filed_at")
    private OffsetDateTime filedAt;

    @Column(name = "total_output_tax", nullable = false)
    @Builder.Default
    private Long totalOutputTax = 0L;

    @Column(name = "total_input_tax", nullable = false)
    @Builder.Default
    private Long totalInputTax = 0L;

    @Column(name = "net_payable", nullable = false)
    @Builder.Default
    private Long netPayable = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaxReturnStatus status = TaxReturnStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "taxReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaxReturnLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
