package com.bracit.fisprocess.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name = "fis_reconciliation_match")
@Getter @Setter @EqualsAndHashCode(onlyExplicitlyIncluded = true) @Builder @NoArgsConstructor @AllArgsConstructor
public class ReconciliationMatch {
    @EqualsAndHashCode.Include @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "reconciliation_id", nullable = false) private UUID reconciliationId;
    @Column(name = "statement_line_id", nullable = false) private UUID statementLineId;
    @Nullable @Column(name = "journal_line_id") private UUID journalLineId;
    @Column(name = "amount", nullable = false) private Long amount;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = OffsetDateTime.now(); }
}
