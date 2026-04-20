package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fis_fixedasset", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "asset_tag"}))
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixedAsset {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "asset_tag", nullable = false, length = 50)
    private String assetTag;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "acquisition_cost", nullable = false)
    private Long acquisitionCost;

    @Column(name = "salvage_value")
    private Long salvageValue;

    @Column(name = "useful_life_months", nullable = false)
    private Integer usefulLifeMonths;

    @Column(name = "depreciation_method", nullable = false, length = 30)
    private String depreciationMethod;

    @Column(name = "accumulated_depreciation")
    @Builder.Default
    private Long accumulatedDepreciation = 0L;

    @Column(name = "net_book_value")
    private Long netBookValue;

    @Column(name = "location", length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AssetStatus status = AssetStatus.ACTIVE;

    @Column(name = "disposal_date")
    private LocalDate disposalDate;

    @Column(name = "disposal_proceeds")
    private Long disposalProceeds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public enum AssetStatus {
        ACTIVE, FULLY_DEPRECIATED, DISPOSED
    }
}