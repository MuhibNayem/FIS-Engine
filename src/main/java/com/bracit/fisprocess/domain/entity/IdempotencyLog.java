package com.bracit.fisprocess.domain.entity;

import com.bracit.fisprocess.domain.enums.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Durable idempotency record in PostgreSQL.
 * Primary idempotency check happens in Redis; this is the fallback.
 */
@Entity
@Table(name = "fis_idempotency_log")
@IdClass(IdempotencyLog.IdempotencyLogId.class)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyLog {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false, columnDefinition = "json")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    /**
     * Composite primary key for {@link IdempotencyLog}.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdempotencyLogId implements Serializable {
        private UUID tenantId;
        private String eventId;
    }
}
