package com.bracit.fisprocess.domain.entity;

import com.bracit.fisprocess.domain.enums.JournalWorkflowStatus;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mutable approval workflow record for manual journal entries.
 * Ledger append-only starts only after approval, when immutable rows are posted.
 */
@Entity
@Table(name = "fis_journal_workflow")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class JournalWorkflow {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "workflow_id", nullable = false, updatable = false)
    private UUID workflowId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "posted_date", nullable = false)
    private LocalDate postedDate;

    @Nullable
    private String description;

    @Nullable
    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "transaction_currency", nullable = false, length = 3)
    private String transactionCurrency;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JournalWorkflowStatus status;

    @Nullable
    @Column(name = "submitted_by")
    private String submittedBy;

    @Nullable
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Nullable
    @Column(name = "approved_by")
    private String approvedBy;

    @Nullable
    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Nullable
    @Column(name = "rejected_by")
    private String rejectedBy;

    @Nullable
    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Nullable
    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Nullable
    @Column(name = "posted_journal_entry_id")
    private UUID postedJournalEntryId;

    @Nullable
    @Column(name = "traceparent")
    private String traceparent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JournalWorkflowLine> lines = new ArrayList<>();

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

    public void addLine(JournalWorkflowLine line) {
        lines.add(line);
        line.setWorkflow(this);
    }
}
