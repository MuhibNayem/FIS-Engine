package com.bracit.fisprocess.domain.entity;
import com.bracit.fisprocess.domain.enums.BankAccountStatus;
import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(name = "fis_bank_account", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id","account_number"}))
@Getter @Setter @EqualsAndHashCode(onlyExplicitlyIncluded = true) @Builder @NoArgsConstructor @AllArgsConstructor
public class BankAccount {
    @EqualsAndHashCode.Include @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "account_number", nullable = false, length = 50) private String accountNumber;
    @Column(name = "bank_name", nullable = false, length = 100) private String bankName;
    @Nullable @Column(name = "branch_code", length = 20) private String branchCode;
    @Column(name = "currency", nullable = false, length = 3) @Builder.Default private String currency = "USD";
    @Nullable @Column(name = "gl_account_code", length = 50) private String glAccountCode;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) @Builder.Default private BankAccountStatus status = BankAccountStatus.ACTIVE;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @PrePersist protected void onCreate() { OffsetDateTime n = OffsetDateTime.now(); if(createdAt==null) createdAt=n; updatedAt=n; }
    @PreUpdate protected void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
