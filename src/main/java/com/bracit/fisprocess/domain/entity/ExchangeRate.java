package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Daily FX conversion rate per tenant and currency pair.
 */
@Entity
@Table(name = "fis_exchange_rate", uniqueConstraints = @UniqueConstraint(columnNames = {
        "tenant_id", "source_currency", "target_currency", "effective_date"
}))
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "rate_id", nullable = false, updatable = false)
    private UUID rateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (rateId == null) {
            rateId = UUID.randomUUID();
        }
    }
}
