package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Durable outbox record for reliable downstream event publication.
 */
@Entity
@Table(name = "fis_outbox")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "outbox_id", nullable = false, updatable = false)
    private UUID outboxId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Nullable
    @Column(name = "traceparent", length = 255)
    private String traceparent;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private boolean published = false;

    @Nullable
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Number of publish attempts for this event.
     * Incremented on each failed relay attempt.
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /**
     * Maximum number of retry attempts before the event is moved to DLQ.
     */
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 50;

    /**
     * Indicates whether this event has been moved to the dead letter queue
     * after exhausting all retry attempts.
     */
    @Column(name = "dlq", nullable = false)
    @Builder.Default
    private boolean dlq = false;

    /**
     * Last error message from a failed publish attempt.
     * Truncated to 2048 characters to prevent excessive storage.
     */
    @Nullable
    @Column(name = "last_error", length = 2048)
    private String lastError;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
