package com.bracit.fisprocess.domain.entity;
import com.bracit.fisprocess.domain.enums.ReconciliationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name = "fis_reconciliation")
@Getter @Setter @EqualsAndHashCode(onlyExplicitlyIncluded = true) @Builder @NoArgsConstructor @AllArgsConstructor
public class Reconciliation {
    @EqualsAndHashCode.Include @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "bank_account_id", nullable = false) private UUID bankAccountId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Nullable @Column(name = "reconciled_at") private OffsetDateTime reconciledAt;
    @Nullable @Column(name = "reconciled_by", length = 100) private String reconciledBy;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) @Builder.Default private ReconciliationStatus status = ReconciliationStatus.IN_PROGRESS;
    @Column(name = "total_matched") @Builder.Default private Long totalMatched = 0L;
    @Column(name = "total_unmatched") @Builder.Default private Long totalUnmatched = 0L;
    @Column(name = "discrepancy") @Builder.Default private Long discrepancy = 0L;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = OffsetDateTime.now(); }
}
