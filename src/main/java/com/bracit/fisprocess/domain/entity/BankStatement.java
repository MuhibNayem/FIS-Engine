package com.bracit.fisprocess.domain.entity;
import com.bracit.fisprocess.domain.enums.BankStatementStatus;
import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity @Table(name = "fis_bank_statement")
@Getter @Setter @EqualsAndHashCode(onlyExplicitlyIncluded = true) @Builder @NoArgsConstructor @AllArgsConstructor
public class BankStatement implements Persistable<UUID> {
    @EqualsAndHashCode.Include @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "bank_account_id", nullable = false) private UUID bankAccountId;
    @Column(name = "statement_date", nullable = false) private LocalDate statementDate;
    @Column(name = "opening_balance", nullable = false) @Builder.Default private Long openingBalance = 0L;
    @Column(name = "closing_balance", nullable = false) @Builder.Default private Long closingBalance = 0L;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) @Builder.Default private BankStatementStatus status = BankStatementStatus.IMPORTED;
    @Nullable @Column(name = "imported_by", length = 100) private String importedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true) @Builder.Default private List<BankStatementLine> lines = new ArrayList<>();
    @Builder.Default private transient boolean isNew = true;
    @PrePersist protected void onCreate() { createdAt = OffsetDateTime.now(); }
    @PostPersist @PostLoad protected void markNotNew() { isNew = false; }
    @Override public boolean isNew() { return isNew; }
    public void addLine(BankStatementLine l) { lines.add(l); l.setStatement(this); }
}
