package com.bracit.fisprocess.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name = "fis_bank_statement_line")
@Getter @Setter @EqualsAndHashCode(onlyExplicitlyIncluded = true) @Builder @NoArgsConstructor @AllArgsConstructor
public class BankStatementLine {
    @EqualsAndHashCode.Include @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "statement_id", nullable = false) private BankStatement statement;
    @Column(name = "date", nullable = false) private LocalDate date;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false) private Long amount;
    @Nullable @Column(length = 100) private String reference;
    @Column(name = "matched", nullable = false) @Builder.Default private boolean matched = false;
    @Nullable @Column(name = "matched_journal_line_id") private UUID matchedJournalLineId;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = OffsetDateTime.now(); }
}
