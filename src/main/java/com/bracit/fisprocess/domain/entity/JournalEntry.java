package com.bracit.fisprocess.domain.entity;

import com.bracit.fisprocess.domain.enums.JournalStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable ledger entry representing a double-entry financial transaction.
 * <p>
 * Once posted, rows in this table are never updated or deleted by application
 * logic.
 * Corrections are handled via reversal entries.
 */
@Entity
@Table(name = "fis_journal_entry")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry implements Persistable<UUID> {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "journal_entry_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "posted_date", nullable = false)
    private LocalDate postedDate;

    @Nullable
    private String description;

    @Nullable
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalStatus status;

    @Nullable
    @Column(name = "reversal_of_id")
    private UUID reversalOfId;

    @Column(name = "transaction_currency", nullable = false, length = 3)
    private String transactionCurrency;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "previous_hash", nullable = false)
    private String previousHash;

    @Column(nullable = false)
    private String hash;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /**
     * Adds a line to this journal entry, maintaining bidirectional relationship.
     */
    public void addLine(JournalLine line) {
        lines.add(line);
        line.setJournalEntry(this);
    }
}
