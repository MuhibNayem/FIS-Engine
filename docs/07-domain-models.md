# Low-Level Design: Domain Entities, DTOs & Service Interfaces

This document defines the complete Java 25 domain model for the FIS engine, including JPA Entities, Request/Response DTOs, Enums, and Service Interface contracts.

## 1. Core Principles
- **Null Safety:** All packages annotated with `@NullMarked` (JSpecify).
- **Boilerplate Reduction:** Lombok (`@Data`, `@Builder`, `@Value`, `@NoArgsConstructor`, `@AllArgsConstructor`).
- **Precision:** `Long` for `amountCents` (exact monetary values). `BigDecimal` never exposed in DTOs — only at the currency conversion service layer.
- **Mapping:** ModelMapper converts between DTOs and Entities at service boundaries.

---

## 2. Enums

```java
public enum AccountType {
    ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
}

public enum JournalStatus {
    POSTED, REVERSAL, CORRECTION
}

public enum PeriodStatus {
    OPEN, SOFT_CLOSED, HARD_CLOSED
}

public enum IdempotencyStatus {
    PROCESSING, COMPLETED, FAILED
}

public enum AuditAction {
    CREATED, UPDATED, DEACTIVATED, STATE_CHANGED
}
```

---

## 3. JPA Entities

### 3.1 BusinessEntity (Tenant)

```java
@Entity
@Table(name = "fis_business_entity")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusinessEntity {

    @Id @GeneratedValue
    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Nullable
    private String legalName;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
```

### 3.2 Account

```java
@Entity
@Table(name = "fis_account", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "code"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Account {

    @Id @GeneratedValue
    @Column(name = "account_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType type;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "current_balance", nullable = false)
    @Builder.Default
    private Long currentBalance = 0L;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    private Account parentAccount;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
```

### 3.3 AccountingPeriod

```java
@Entity
@Table(name = "fis_accounting_period", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountingPeriod {

    @Id @GeneratedValue
    @Column(name = "period_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PeriodStatus status = PeriodStatus.OPEN;

    @Nullable
    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Nullable
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = OffsetDateTime.now(); }
}
```

### 3.4 ExchangeRate

```java
@Entity
@Table(name = "fis_exchange_rate",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "source_currency", "target_currency", "effective_date"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExchangeRate {

    @Id @GeneratedValue
    @Column(name = "rate_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = OffsetDateTime.now(); }
}
```

### 3.5 JournalEntry

```java
@Entity
@Table(name = "fis_journal_entry")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JournalEntry {

    @Id @GeneratedValue
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
    private BigDecimal exchangeRate;

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

    @PrePersist
    protected void onCreate() { createdAt = OffsetDateTime.now(); }

    public void addLine(JournalLine line) {
        lines.add(line);
        line.setJournalEntry(this);
    }
}
```

### 3.6 JournalLine

```java
@Entity
@Table(name = "fis_journal_line")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JournalLine {

    @Id @GeneratedValue
    @Column(name = "line_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    @ToString.Exclude
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "base_amount", nullable = false)
    private Long baseAmount;

    @Column(name = "is_credit", nullable = false)
    private boolean isCredit;

    @Nullable
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> dimensions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = OffsetDateTime.now(); }
}
```

---

## 4. Request DTOs

### 4.1 FinancialEventDto (Intake)

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinancialEventDto {
    private String eventId;
    private String eventType;
    private OffsetDateTime occurredAt;
    private String transactionCurrency;
    private Map<String, Object> payload;
    private Map<String, String> dimensions;
}
```

### 4.2 CreateJournalEntryRequestDto

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateJournalEntryRequestDto {
    private String eventId;
    private LocalDate postedDate;
    @Nullable private String description;
    @Nullable private String referenceId;
    private String transactionCurrency;
    private List<JournalLineRequestDto> lines;
}
```

### 4.3 JournalLineRequestDto

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JournalLineRequestDto {
    private String accountCode;
    private Long amountCents;
    private boolean isCredit;
    @Nullable private Map<String, String> dimensions;
}
```

### 4.4 CreateAccountRequestDto

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateAccountRequestDto {
    private String code;
    private String name;
    private AccountType accountType;
    private String currencyCode;
    @Nullable private String parentAccountCode;
}
```

### 4.5 UpdateAccountRequestDto

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateAccountRequestDto {
    @Nullable private String name;
    @Nullable private Boolean isActive;
}
```

### 4.6 CreateAccountingPeriodRequestDto

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateAccountingPeriodRequestDto {
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
}
```

### 4.7 PeriodStatusChangeRequestDto

```java
@Data @NoArgsConstructor @AllArgsConstructor
public class PeriodStatusChangeRequestDto {
    private String action; // OPEN, SOFT_CLOSE, HARD_CLOSE, REOPEN
}
```

### 4.8 CreateMappingRuleRequestDto

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateMappingRuleRequestDto {
    private String eventType;
    @Nullable private String description;
    private List<MappingRuleLineDto> lines;
}

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MappingRuleLineDto {
    private String accountCodeExpression;
    private boolean isCredit;
    private String amountExpression;
}
```

### 4.9 ReversalRequestDto

```java
@Data @NoArgsConstructor @AllArgsConstructor
public class ReversalRequestDto {
    private String eventId;
    private String reason;
}
```

### 4.10 ExchangeRateUploadDto

```java
@Data @NoArgsConstructor @AllArgsConstructor
public class ExchangeRateUploadDto {
    private List<ExchangeRateEntryDto> rates;
}

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExchangeRateEntryDto {
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal rate;
    private LocalDate effectiveDate;
}
```

---

## 5. Response DTOs

### 5.1 JournalEntryResponseDto

```java
@Data @Builder
public class JournalEntryResponseDto {
    private UUID journalEntryId;
    private LocalDate postedDate;
    private JournalStatus status;
    @Nullable private String description;
    @Nullable private String referenceId;
    private String transactionCurrency;
    private String baseCurrency;
    private BigDecimal exchangeRate;
    private int lineCount;
    @Nullable private UUID reversalOfId;
    private String createdBy;
    private OffsetDateTime createdAt;
}
```

### 5.2 AccountResponseDto

```java
@Data @Builder
public class AccountResponseDto {
    private UUID accountId;
    private String code;
    private String name;
    private AccountType accountType;
    private String currencyCode;
    private Long currentBalanceCents;
    private String formattedBalance;
    private boolean isActive;
    @Nullable private String parentAccountCode;
    private OffsetDateTime asOf;
}
```

### 5.3 AccountingPeriodResponseDto

```java
@Data @Builder
public class AccountingPeriodResponseDto {
    private UUID periodId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private PeriodStatus status;
    @Nullable private String closedBy;
    @Nullable private OffsetDateTime closedAt;
}
```

---

## 6. Service Interfaces

These define the strict contract for the internal processing pipeline.

```java
public interface IdempotencyService {
    boolean isNewRequest(UUID tenantId, String eventId, String payloadHash);
    void markCompleted(UUID tenantId, String eventId, String responseBody);
    void markFailed(UUID tenantId, String eventId);
    @Nullable String getCachedResponse(UUID tenantId, String eventId);
}

public interface RuleMappingService {
    DraftJournalEntry mapEventToJournalEntry(UUID tenantId, FinancialEventDto event);
}

public interface PeriodValidationService {
    void validatePeriodIsOpen(UUID tenantId, LocalDate postedDate);
}

public interface JournalEntryValidationService {
    void validate(DraftJournalEntry draft);
}

public interface MultiCurrencyService {
    BigDecimal getExchangeRate(UUID tenantId, String sourceCurrency, String targetCurrency, LocalDate date);
    Long convertToBaseCurrency(Long amountCents, BigDecimal exchangeRate);
}

public interface LedgerPersistenceService {
    JournalEntry persist(DraftJournalEntry draft);
}

public interface HashChainService {
    String computeHash(UUID journalEntryId, String previousHash, OffsetDateTime createdAt);
    String getLatestHash(UUID tenantId);
}

public interface JournalReversalService {
    JournalEntry reverseEntry(UUID tenantId, UUID journalEntryId, String reason, String performedBy);
}
```

---

## 7. Ledger Locking Service Interface

```java
public interface LedgerLockingService {
    /**
     * Acquires a pessimistic lock on the account row using SELECT ... FOR UPDATE,
     * then atomically updates the balance.
     */
    void updateAccountBalance(UUID accountId, Long deltaAmountCents);
}
```

---

## 8. Draft Journal Entry (Internal)

Used as the intermediate representation between the mapping engine and the persistence layer.

```java
@Data @Builder
public class DraftJournalEntry {
    private UUID tenantId;
    private String eventId;
    private LocalDate postedDate;
    @Nullable private String description;
    @Nullable private String referenceId;
    private String transactionCurrency;
    private String baseCurrency;
    private BigDecimal exchangeRate;
    private String createdBy;
    @Nullable private UUID reversalOfId;
    private List<DraftJournalLine> lines;
}

@Data @Builder
public class DraftJournalLine {
    private String accountCode;
    private Long amountCents;
    private Long baseAmountCents;
    private boolean isCredit;
    @Nullable private Map<String, String> dimensions;
}
```
