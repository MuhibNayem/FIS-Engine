package com.bracit.fisprocess.domain.entity;

import com.bracit.fisprocess.domain.enums.TaxDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A line item within a Tax Return — tracks taxable amounts and tax per rate.
 */
@Entity
@Table(name = "fis_tax_return_line")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReturnLine {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_return_line_id", updatable = false, nullable = false)
    private UUID taxReturnLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private TaxReturn taxReturn;

    @Column(name = "tax_rate_id", nullable = false)
    private UUID taxRateId;

    @Column(name = "taxable_amount", nullable = false)
    private Long taxableAmount;

    @Column(name = "tax_amount", nullable = false)
    private Long taxAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxDirection direction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
