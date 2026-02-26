# Development Implementation Roadmap

## Generic Financial Information System (FIS) Engine

---

| Field | Value |
| :---- | :---- |
| **Document Title** | Phased Development Implementation Roadmap |
| **Version** | 1.0 |
| **Date** | February 25, 2026 |
| **Prepared By** | Engineering Division |
| **Derived From** | SRS v1.0 (docs/SRS.md) |
| **Classification** | Confidential — Internal Use Only |

---

## Executive Summary

This document breaks down the FIS Engine development into **six sequentially dependent phases**, each producing a **working, testable deliverable**. Phases are ordered by strict dependency — no phase can begin until all of its dependencies from preceding phases are complete. Each phase lists its deliverables, SRS requirements addressed, acceptance criteria, and estimated effort.

**Total estimated timeline: 14–18 sprints (28–36 weeks at 2-week sprint cadence)**

---

## 📊 Progress Summary

> **Last Updated:** February 26, 2026

| Phase | Status | Tests | Notes |
| :---- | :---- | :---- | :---- |
| **Phase 1** — Foundation & Infrastructure | ✅ **Complete** | 13/13 ✅ | All deliverables shipped, verified against Testcontainers PostgreSQL |
| **Phase 2** — Core Ledger Engine | ✅ **Complete** | Core + concurrency ✅ | Journal pipeline, hash chain, `accountCode` filter, append-only DB enforcement |
| **Phase 3** — Event-Driven Intake & Idempotency | ✅ **Complete** | Integration + regression ✅ | `/v1/events`, RabbitMQ consumer, Redis/PostgreSQL idempotency, outbox relay |
| **Phase 4** — Multi-Currency & Accounting Periods | ✅ **Complete** | Integration ✅ | Period controls, FX conversion, exchange-rate APIs, and pipeline enforcement |
| **Phase 5** — Reversals, Rules Engine & Audit | ✅ **Complete** | Integration + regression ✅ | Reversal/correction APIs, mapping rules engine + CRUD, audit trail, period-end revaluation |
| **Phase 6** — Observability & Production Readiness | ✅ **Complete** | Security + regression ✅ | JWT/RBAC, trace propagation, deployment artifacts, load-test assets, runbooks |

### What Was Done (Phase 1)
- Build configuration with Spring Boot 4.0.3, Flyway, Testcontainers, ModelMapper, JSpecify
- Added `spring-boot-starter-amqp`, `spring-boot-starter-opentelemetry`, and Testcontainers RabbitMQ module
- Flyway V1–V2 migrations (DB-agnostic — works on both H2 and PostgreSQL)
- Domain entities: `BusinessEntity`, `Account`, `AccountType` enum
- Repositories: `BusinessEntityRepository`, `AccountRepository` (tenant-scoped, JPQL filtered queries)
- DTOs: `CreateAccountRequestDto`, `UpdateAccountRequestDto`, `AccountResponseDto`
- Service layer: `AccountService` / `AccountServiceImpl` with balance formatting
- Controller: `AccountController` at `/v1/accounts` (POST, GET, PATCH, list with pagination)
- RFC 7807 exception handling: `GlobalExceptionHandler`, `FisBusinessException` hierarchy
- `@NullMarked` on all 11 packages; `ModelMapperConfig` bean
- `application.yml` with `dev`/`test`/`prod` Spring profiles + RabbitMQ config
- `AbstractIntegrationTest` base class for DRY Testcontainers PostgreSQL setup
- **10 unit tests** (AccountServiceImpl) + **1 Flyway migration test** + **8 controller integration tests** (MockMvc + Testcontainers PG)

### What Was Done (Phase 2)
- Flyway migrations V5–V7 for `fis_idempotency_log`, `fis_journal_entry`, `fis_journal_line`
- PostgreSQL append-only enforcement for journal tables (`V11__enforce_append_only_ledger.sql`)
- Double-entry Journal Entry pipeline with `Sum(Debits) == Sum(Credits)` validation
- `SELECT FOR UPDATE` deterministic lock ordering for hot account concurrency safety
- SHA-256 hash chain persistence for Journal Entries
- Journal Entry REST APIs:
  - `POST /v1/journal-entries`
  - `GET /v1/journal-entries/{id}`
  - `GET /v1/journal-entries` with filters: `postedDateFrom`, `postedDateTo`, `accountCode`, `status`, `referenceId`
- Integration and concurrency testing:
  - 100 concurrent postings to same account
  - deadlock-resilience test with reversed multi-account line order

Migration numbering note: `V3`, `V4`, `V8`, and `V9` are intentionally unused in the current Flyway chain.

### Next Steps
- Track and close post-implementation hardening/remediation in `docs/10-audit-remediation-plan.md`

---

## Phase Dependency Map

```
Phase 1: Foundation & Infrastructure
    │
    ├──► Phase 2: Core Ledger Engine
    │        │
    │        ├──► Phase 3: Event-Driven Intake & Idempotency
    │        │        │
    │        │        └──► Phase 4: Multi-Currency & Accounting Periods
    │        │                 │
    │        │                 └──► Phase 5: Reversals, Rules Engine & Audit
    │        │                          │
    │        │                          └──► Phase 6: Observability, Hardening & Production Readiness
    │        │
    │        └──► (Phase 3 can begin as soon as Phase 2 core is stable)
    │
    └──► (Phase 2 begins immediately after Phase 1)
```

---

## [x] Phase 1: Foundation & Infrastructure Setup

### Objective
Establish the project skeleton, build pipeline, database connectivity, schema migration framework, and the foundational domain entities that every subsequent phase depends on.

### Duration: 2–3 Sprints

### SRS Requirements Addressed
| ID | Requirement |
| :---- | :---- |
| C-01 | All monetary values as `BIGINT` / `NUMERIC` |
| C-03 | JSpecify `@NullMarked` on all packages |
| C-04 | Hibernate `ddl-auto = validate`, Flyway manages schema |
| C-06 | Jakarta EE 11 namespaces |
| NFR-11 | Flyway versioned migrations |
| NFR-17 | Lombok usage across codebase |
| NFR-23 | Actuator health endpoints |

### Deliverables

#### [x] 1.1 — Gradle Project Initialization
- Initialize Spring Boot 4.0.3 project via `spring init` or manual `build.gradle`
- Configure Gradle with all dependencies:
  - `spring-boot-starter-webmvc`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-amqp`
  - `spring-boot-starter-data-redis`
  - `spring-boot-starter-opentelemetry`
  - `spring-boot-starter-actuator`
  - `flyway-core` + `flyway-database-postgresql`
  - `lombok`, `modelmapper`, `jspecify`
  - `spring-boot-starter-test`, `testcontainers` (PostgreSQL, RabbitMQ, Redis)
- Configure `application.yml` with environment-variable-driven profiles (`dev`, `test`, `prod`)
- Set `spring.jpa.hibernate.ddl-auto=validate`

#### [x] 1.2 — Flyway Migrations V1–V2
- `V1__create_business_entity.sql` — `fis_business_entity` table
- `V2__create_accounts.sql` — `fis_account` table with parent-child hierarchy

#### [x] 1.3 — Domain Entities & Repositories
- `BusinessEntity` JPA entity with `@NullMarked` package-info
- `Account` JPA entity with `AccountType` enum
- `BusinessEntityRepository` (Spring Data JPA)
- `AccountRepository` (Spring Data JPA)

#### [x] 1.4 — Account REST APIs (CRUD)
- `POST /v1/accounts` — Create account
- `GET /v1/accounts` — List accounts (paginated, filterable by `accountType`, `isActive`)
- `GET /v1/accounts/{code}` — Get account by code
- `PATCH /v1/accounts/{code}` — Update name / deactivate
- `CreateAccountRequestDto`, `UpdateAccountRequestDto`, `AccountResponseDto`
- ModelMapper configuration bean

#### [x] 1.5 — Global Exception Handler
- `@ControllerAdvice` extending `ResponseEntityExceptionHandler`
- RFC 7807 `ProblemDetail` responses for:
  - `ValidationFailedException` → 400
  - `AccountNotFoundException` → 404
  - `FisBusinessException` (base class for domain errors)

#### [x] 1.6 — Testing Foundation
- `AbstractIntegrationTest` base class with Testcontainers (PostgreSQL)
- Verify Flyway migrations run cleanly on Testcontainers
- Unit tests for Account CRUD service
- Integration tests for Account REST endpoints

### Acceptance Criteria
- [x] `./gradlew build` passes with zero errors
- [x] Flyway migrations V1–V2 execute successfully on Testcontainers PostgreSQL
- [x] Account CRUD APIs return correct responses (201, 200, 404, 400)
- [x] All error responses conform to RFC 7807 `ProblemDetail`
- [x] JSpecify `@NullMarked` present on every `package-info.java`
- [x] Actuator `/actuator/health` returns `UP`

---

## [x] Phase 2: Core Ledger Engine

### Objective
Implement the heart of the system — the double-entry Journal Entry processing pipeline with balance updates, validation, and cryptographic hash chain integrity.

### Duration: 3–4 Sprints

### Dependencies: Phase 1 complete

### SRS Requirements Addressed
| ID | Requirement |
| :---- | :---- |
| FR-07 | The accounting equation is enforced |
| FR-08 | `current_balance` updated transactionally |
| FR-09 | REST APIs for Journal Entry intake |
| FR-10 | Sum(Debits) == Sum(Credits) validation |
| FR-12 | JSONB dimensional tags on Journal Lines |
| FR-13 | Journal Entry query APIs with pagination and filters |
| FR-14 | SHA-256 cryptographic hash chain |
| NFR-02 | Hot account locking via `SELECT FOR UPDATE` |

### Deliverables

#### [x] 2.1 — Flyway Migrations V5–V7
- `V5__create_idempotency_log.sql` — `fis_idempotency_log` table
- `V6__create_journal_entries.sql` — `fis_journal_entry` table
- `V7__create_journal_lines.sql` — `fis_journal_line` table

#### [x] 2.2 — Domain Entities
- `IdempotencyLog` entity with `IdempotencyStatus` enum
- `JournalEntry` entity with `JournalStatus` enum
- `JournalLine` entity with JSONB dimensions support (`@Type(JsonType.class)`)
- `DraftJournalEntry` + `DraftJournalLine` internal processing models

#### [x] 2.3 — Request/Response DTOs
- `CreateJournalEntryRequestDto`
- `JournalLineRequestDto`
- `JournalEntryResponseDto`

#### [x] 2.4 — Core Service Pipeline (Steps 5, 7, 8, 9)
- `JournalEntryValidationService`
  - Validates `Sum(Debits) == Sum(Credits)`
  - Validates all referenced account codes exist and are active
  - Validates `amount > 0` for every line
- `LedgerLockingService`
  - `SELECT account_id, current_balance FROM fis_account WHERE account_id IN (:ids) ORDER BY account_id FOR UPDATE`
  - Deterministic account lock ordering to reduce deadlocks under concurrent multi-account postings
  - Atomically updates `current_balance` for each affected account
- `LedgerPersistenceService`
  - Within a single `@Transactional` boundary:
    - Persists `JournalEntry` (status = POSTED)
    - Persists all `JournalLine` records
    - Updates account balances via `LedgerLockingService`
    - Computes and stores hash chain
- `HashChainService`
  - `SHA-256(journal_entry_id + previous_hash + created_at)`
  - Retrieves latest hash from `fis_journal_entry` ordered by `created_at DESC LIMIT 1`

#### [x] 2.5 — Journal Entry REST API
- `POST /v1/journal-entries` — Post a manual Journal Entry
- `GET /v1/journal-entries` — Query with filters: `postedDateFrom`, `postedDateTo`, `accountCode`, `status`, `referenceId`, pagination (`page`, `size`)

#### [x] 2.6 — Testing
- Unit tests: `JournalEntryValidationService` (balanced, unbalanced, inactive account, missing account)
- Unit tests: `HashChainService` (hash computation, chain integrity)
- Integration tests: Full `POST /v1/journal-entries` → balance update → hash chain verification
- Integration tests: `GET /v1/journal-entries` with all filter combinations
- Concurrency test: 100 concurrent Virtual Threads posting to the same account — verify no balance corruption
- Concurrency test: deadlock resilience under 2+ account postings with deterministic lock ordering

### Acceptance Criteria
- [x] A balanced Journal Entry posts successfully, balance updates atomically
- [x] An unbalanced Journal Entry is rejected with `422 /problems/unbalanced-entry`
- [x] Journal Entries targeting inactive or non-existent accounts are rejected
- [x] Hash chain is unbroken across sequential entries
- [x] 100 concurrent writes to the same account produce correct final balance
- [x] Query API returns correct filtered/paginated results

---

## [x] Phase 3: Event-Driven Intake & Idempotency

### Objective
Wire up the asynchronous event ingestion pipeline via RabbitMQ and the Redis-based idempotency layer, enabling upstream domain services to fire-and-forget financial events.

### Duration: 2–3 Sprints

### Dependencies: Phase 2 core ledger operational

### SRS Requirements Addressed
| ID | Requirement |
| :---- | :---- |
| FR-31 | Quorum Queues for intake |
| FR-32 | DLQ for failed messages |
| FR-33 | `basicAck` after full pipeline completion |
| FR-34 | Domain event publishing via Transactional Outbox |
| FR-35 | Every event must include `eventId` as Idempotency Key |
| FR-36 | Redis `SET NX EX` for duplicate detection |
| FR-37 | Key pattern: `fis:ik:{tenant_id}:{event_id}` with 72h TTL |
| FR-38 | Duplicate with matching hash → cached response; mismatching hash → 409 |
| FR-39 | Durable idempotency record in PostgreSQL |
| NFR-07 | Zero data loss guarantee |
| NFR-08 | Message durability (delivery mode 2) |

### Deliverables

#### [x] 3.1 — RabbitMQ Infrastructure Configuration
- `RabbitMQConfig` class:
  - Declares `fis.events.exchange` (Topic)
  - Declares `fis.ingestion.queue` (Quorum, bound with `*.*.*`)
  - Declares `fis.dlx.exchange` (Direct)
  - Declares `fis.ingestion.dlq.queue` (Quorum, bound with `fis.ingestion.dlq`)
  - Declares `fis.domain.exchange` (Topic) for outbound events
- `application.yml` consumer settings:
  - `acknowledge-mode: manual`
  - `prefetch: 50` (configurable via `RABBITMQ_PREFETCH`)
  - `concurrency: 3 / max-concurrency: 12` (configurable via env)
  - `default-requeue-rejected: false`

#### [x] 3.2 — Redis Idempotency Layer
- `IdempotencyService` interface + `RedisIdempotencyServiceImpl`
  - `checkAndMarkProcessing(tenantId, eventId, payloadHash)` → `SET NX EX 259200`
  - `markCompleted(tenantId, eventId, payloadHash, responseBody)` → `SET XX EX 259200`
  - `markFailed(tenantId, eventId, payloadHash, failureDetail)` → `SET XX EX 259200`
- SHA-256 payload hash computation for duplicate-with-different-payload detection

#### [x] 3.3 — Idempotency Log Persistence
- `IdempotencyLogRepository`
- On successful processing: write `fis_idempotency_log` record (status = `COMPLETED`)
- On failure: write with status = `FAILED`

#### [x] 3.4 — Event Intake REST Endpoint
- `POST /v1/events` — Financial event ingestion
  - Requires `eventId` in payload and `X-Source-System` header
  - Returns `202 Accepted` on success
  - Returns `409 Conflict` on duplicate `eventId` with mismatching payload hash
- `FinancialEventRequestDto`

#### [x] 3.5 — RabbitMQ Consumer
- `@RabbitListener` on `fis.ingestion.queue`
- Extract `eventId` from payload (canonical `ik`)
- Pipeline: `JournalEntryService` posting, then idempotency completion/failure marking
- `channel.basicAck()` only after full commit
- `channel.basicReject(requeue=false)` on validation failure → routes to DLQ
- `channel.basicNack(requeue=true)` on transient infrastructure/runtime failure

#### [x] 3.6 — Transactional Outbox
- `fis_outbox` table (Flyway migration)
- After successful JE commit, write `fis.journal.posted` event to outbox table within the same transaction
- Outbox poller (scheduled task) reads unpublished rows and publishes to `fis.domain.exchange`

#### [x] 3.7 — Testing
- Integration tests with Testcontainers (RabbitMQ + Redis + PostgreSQL):
  - Happy path: event published → consumed → JE posted → `basicAck`
  - Duplicate event (same `eventId`, same payload) → accepted idempotently, no duplicate JE
  - Duplicate event (same `eventId`, different payload) → HTTP 409 at ingest API
- Full regression suite run: `./gradlew test`

### Acceptance Criteria
- [x] Events published to RabbitMQ are consumed and produce correct Journal Entries
- [x] Duplicate events (same `eventId`) are idempotently handled — zero duplicate JEs
- [x] Payload hash mismatch on same `eventId` returns HTTP 409 at ingestion API
- [x] Consumer does NOT ack before Postgres commit
- [x] Redis key TTL is 72 hours
- [x] `fis_idempotency_log` contains durable records for all processed events
- [x] Domain events appear in outbox table and are relayed to `fis.domain.exchange`

### Deferred To Phase 5 (Explicit Dependency)
- FR-35 includes reversal/correction write flows.
- Reversal/correction endpoints are delivered in Phase 5, where `eventId` idempotency enforcement will be applied to those endpoints as part of completion for the full FR-35 scope.

---

## [x] Phase 4: Multi-Currency & Accounting Periods

### Objective
Add exchange rate management, automatic currency conversion on Journal Lines, and accounting period lifecycle enforcement.

### Duration: 2–3 Sprints

### Dependencies: Phase 3 complete (idempotency + event pipeline operational)

### SRS Requirements Addressed
| ID | Requirement |
| :---- | :---- |
| FR-19 | Create/manage Accounting Periods per tenant |
| FR-20 | Three states: OPEN, SOFT_CLOSED, HARD_CLOSED |
| FR-21 | Sequential hard close; cascading reopen |
| FR-22 | State transition APIs |
| FR-23 | Business Entity `base_currency` |
| FR-24 | JE records `transaction_currency`, `base_currency`, `exchange_rate` |
| FR-25 | Journal Lines store `amount` + `base_amount` |
| FR-26 | Exchange rate upload API |
| FR-11 | Reject JEs targeting closed periods |

### Deliverables

#### [x] 4.1 — Flyway Migrations V12–V13
- `V12__create_accounting_periods.sql` — `fis_accounting_period` table
- `V13__create_exchange_rates.sql` — `fis_exchange_rate` table

#### [x] 4.2 — Accounting Period Domain
- `AccountingPeriod` entity + `PeriodStatus` enum
- `AccountingPeriodRepository`
- `AccountingPeriodService`:
  - Create period (validates no date overlap within tenant)
  - State transitions with validation:
    - `OPEN → SOFT_CLOSE`: Allowed anytime
    - `SOFT_CLOSE → HARD_CLOSE`: Only if all prior periods are also HARD_CLOSED
    - `HARD_CLOSE → REOPEN`: Must reopen all subsequent periods first
    - `SOFT_CLOSE → OPEN`: Allowed anytime
- `CreateAccountingPeriodRequestDto`, `PeriodStatusChangeRequestDto`, `AccountingPeriodResponseDto`

#### [x] 4.3 — Accounting Period REST APIs
- `POST /v1/accounting-periods` — Create
- `GET /v1/accounting-periods` — List (filterable by `status`)
- `PATCH /v1/accounting-periods/{id}/status` — State transition

#### [x] 4.4 — Period Validation in Pipeline
- `PeriodValidationService` (Pipeline Step 4):
  - Looks up the Accounting Period containing the JE's `postedDate`
  - If `HARD_CLOSED` → reject unconditionally
  - If `SOFT_CLOSED` → reject unless caller has `FIS_ADMIN` role
  - If `OPEN` → allow
  - If no period found → reject (no open period for date)

#### [x] 4.5 — Exchange Rate Domain
- `ExchangeRate` entity
- `ExchangeRateRepository`
- `ExchangeRateService`:
  - Upload batch of daily rates
  - Lookup rate by `(tenantId, sourceCurrency, targetCurrency, effectiveDate)`
  - Fallback to closest prior date if exact date not found
- `ExchangeRateUploadDto`, `ExchangeRateEntryDto`

#### [x] 4.6 — Exchange Rate REST APIs
- `POST /v1/exchange-rates` — Upload batch
- `GET /v1/exchange-rates` — Query by currency pair and date

#### [x] 4.7 — Multi-Currency in Pipeline
- `MultiCurrencyService` (Pipeline Step 6):
  - If `transactionCurrency == baseCurrency` → `exchangeRate = 1.0`, `baseAmount = amount`
  - If different → lookup rate from `fis_exchange_rate`
  - Compute `baseAmount = Math.round(amount * exchangeRate)` for each line
  - Set `transaction_currency`, `base_currency`, `exchange_rate` on JournalEntry

#### [x] 4.8 — Testing
- Unit tests: Period state machine (all valid/invalid transitions)
- Unit tests: Sequential close enforcement, cascading reopen
- Unit tests: MultiCurrencyService (conversion, missing rate, same-currency shortcut)
- Integration tests: Post JE to closed period → `422 /problems/period-closed`
- Integration tests: Post multi-currency JE → verify `base_amount` values
- Integration tests: Exchange rate upload and lookup

### Acceptance Criteria
- [x] Accounting Periods are created per tenant with no date overlaps
- [x] State transitions enforce sequential close and cascading reopen rules
- [x] Journal Entries targeting closed periods are rejected with `422`
- [x] Multi-currency JEs correctly compute and store `base_amount` on every line
- [x] Exchange rate lookups return correct rates; missing rates produce a clear error
- [x] Pipeline correctly integrates Period Validation + Currency Conversion steps

### Carry-Forward Note
- FR-27 (period-end revaluation automation) was carried to Phase 5 and is now implemented.

---

## [x] Phase 5: Reversals, Rules Engine & Audit

### Objective
Complete the feature set with Journal Entry reversals, the configurable mapping rules engine (the "brain" of domain-agnostic translation), and the comprehensive audit trail.

### Duration: 3–4 Sprints

### Dependencies: Phase 4 complete

### SRS Requirements Addressed
| ID | Requirement |
| :---- | :---- |
| FR-15 | Full reversal of posted JE |
| FR-16 | `reversal_of_id` linking |
| FR-17 | Correction (reverse + re-entry) |
| FR-18 | Reject reversal when a reversal entry already exists |
| FR-28 | CRUD APIs for Mapping Rules |
| FR-29 | Rule Lines with account/amount expressions |
| FR-30 | Versioned, auditable mapping rules |
| FR-40 | All admin changes logged to `fis_audit_log` |
| FR-41 | Audit log record structure |
| FR-27 | Period-end revaluation automation for open foreign-currency monetary balances |

### Deliverables

#### [x] 5.1 — Journal Entry Reversal
- `JournalReversalService`:
  - Load original JE and verify no posted reversal exists for `reversal_of_id = original.id`
  - Create mirror JE: swap every Debit to Credit and vice versa
  - Set mirror's `reversal_of_id = original.id`
  - Keep original JE immutable (no update)
  - Operation in a single `@Transactional`
  - Reverse account balance changes atomically
  - Run through hash chain (new entry gets its own hash)
- `POST /v1/journal-entries/{id}/reverse`
  - `ReversalRequestDto` (reason field)
  - Returns `201 Created` with reversal JE ID

#### [x] 5.X — Period-End Revaluation (Carry Forward from Phase 4)
- Implement FR-27 as a production workflow:
  - Revalue outstanding foreign-currency monetary balances at period close date
  - Generate unrealized FX gain/loss journal entries automatically
  - Ensure idempotent generation per `(tenant, period, currency-pair)`
  - Maintain append-only behavior (no mutation of historical JE rows)
  - Provide reconciliation visibility for generated revaluation entries

#### [x] 5.2 — Correction API (Reverse + Re-entry)
- `POST /v1/journal-entries/{id}/correct`
  - Request body contains the corrected JE lines
  - Atomically: reverse original + post new corrected JE
  - Both the reversal and replacement link to original

#### [x] 5.3 — Flyway Migrations V14–V15
- `V14__create_mapping_rules.sql` — `fis_mapping_rule` + `fis_mapping_rule_line` tables
- `V15__create_audit_log_and_revaluation_runs.sql` — `fis_audit_log` + `fis_revaluation_run` tables

#### [x] 5.4 — Mapping Rules Engine
- `MappingRule` + `MappingRuleLine` entities
- `MappingRuleRepository`
- `RuleMappingService` (Pipeline Step 3):
  - Loads active rule by `(tenantId, eventType)`
  - Evaluates expressions against event payload:
    - `accountCodeExpression` → resolves to account code
    - `amountExpression` → resolves to amount from `payload` map (e.g., `${payload.totalAmountCents}`)
  - Uses ModelMapper to produce `DraftJournalEntry` with computed lines
  - Expression engine: Spring Expression Language (SpEL)
- `CreateMappingRuleRequestDto`, `MappingRuleLineDto`

#### [x] 5.5 — Mapping Rules REST APIs
- `POST /v1/mapping-rules` — Create
- `GET /v1/mapping-rules` — List (filterable by `eventType`, `isActive`)
- `PUT /v1/mapping-rules/{id}` — Update (increments `version`)
- `DELETE /v1/mapping-rules/{id}` — Soft delete (`isActive = false`)

#### [x] 5.6 — Full Event Processing Pipeline Integration
- Wire `RuleMappingService` into the RabbitMQ consumer and `/v1/events` flow:
  - `EventIntakeService → IdempotencyService → RuleMappingService → PeriodValidationService → JournalEntryValidationService → MultiCurrencyService → LedgerLockingService → LedgerPersistenceService → HashChainService → EventOutboxService`
- This is the first time the complete 10-step pipeline operates end-to-end for event-driven intake.

#### [x] 5.7 — Audit Log
- `AuditLog` entity + `AuditAction` enum
- `AuditLogRepository`
- `AuditService`:
  - `logChange(tenantId, entityType, entityId, action, oldValue, newValue, performedBy)`
  - Captures JSONB snapshots of old/new state
- Integrate into:
  - Account creation/update/deactivation
  - Mapping Rule creation/update/deactivation
  - Accounting Period state transitions
  - Journal Entry reversals

#### [x] 5.8 — Testing
- Unit tests: Reversal (balanced mirror, original immutable, reject double-reversal)
- Unit tests: SpEL expression evaluation in RuleMappingService
- Integration tests: End-to-end event → mapping rule → JE creation
- Integration tests: Full pipeline with a configured mapping rule
- Integration tests: Audit log contains correct entries for all admin operations
- Integration tests: Correction API (reverse + re-entry in one call)

### Acceptance Criteria
- [x] Reversal creates an exact mirror JE, leaves original unchanged, and reverses balances
- [x] Double-reversal of the same JE is rejected with `409`
- [x] Correction API atomically reverses and replaces in a single call
- [x] Mapping Rules translate events into correct JEs via SpEL expressions
- [x] Full 10-step pipeline processes events end-to-end
- [x] Audit log captures all admin operations with before/after JSONB snapshots
- [x] Mapping Rule version increments on update

---

## [x] Phase 6: Observability, Hardening & Production Readiness

### Objective
Add distributed tracing across the full pipeline, harden security boundaries, perform load testing, and prepare the system for production deployment.

### Duration: 2–3 Sprints

### Dependencies: Phase 5 complete (all features operational)

### SRS Requirements Addressed
| ID | Requirement |
| :---- | :---- |
| NFR-01 | ≥ 10,000 events/second throughput |
| NFR-02 | REST p99 < 200ms |
| NFR-06 | 99.95% uptime SLA |
| NFR-09 | RBAC enforcement (FIS_ADMIN, FIS_ACCOUNTANT, FIS_READER) |
| NFR-10 | TLS 1.3 inter-service encryption |
| NFR-12 | JWT validation on every request |
| NFR-14 | OpenTelemetry trace IDs across RabbitMQ + REST |
| NFR-21 | `spring-boot-starter-opentelemetry` integration |
| NFR-22 | W3C Traceparent propagation |
| NFR-23 | Health/readiness probes |

### Deliverables

#### [x] 6.1 — OpenTelemetry Integration
- Configure `spring-boot-starter-opentelemetry`
- Structured SLF4J log format with `traceId` and `spanId` in every log line
- W3C Traceparent header propagation:
  - Incoming REST request → spans through service pipeline → Postgres
  - RabbitMQ message headers carry trace context
  - Outbound events published with trace context
- Export to OpenTelemetry Collector (configurable endpoint)

#### [x] 6.2 — Security Hardening
- Spring Security configuration:
  - JWT filter validates `Authorization: Bearer` on every request
  - Extract roles from JWT claims
  - Method-level security with `@PreAuthorize`:
    - `FIS_ADMIN`: Full access
    - `FIS_ACCOUNTANT`: Post JEs, reverse, view
    - `FIS_READER`: View-only
- TLS configuration for Redis and RabbitMQ connections
- `X-Tenant-Id` validation: tenant must exist and be active

#### [x] 6.3 — Performance Optimization
- Database:
  - Verify all indexes from DDL are optimized for query patterns
  - `EXPLAIN ANALYZE` on all critical queries
  - Connection pool tuning (HikariCP `maximum-pool-size`)
- Redis:
  - Connection pool configuration
  - Pipeline batching for bulk idempotency checks (if applicable)
- RabbitMQ:
  - Consumer prefetch tuning based on load test results

#### [x] 6.4 — Load & Stress Testing
- JMeter or Gatling test suite:
  - **Throughput test**: 10,000 events/second sustained for 10 minutes
  - **Latency test**: p99 < 200ms for `POST /v1/journal-entries`
  - **Hot account test**: 1,000 concurrent writes to single account
  - **Burst test**: 50,000 events in 5 seconds spike
- Document results vs. SRS NFR targets

#### [x] 6.5 — Kubernetes Deployment Manifests
- `Deployment`, `Service`, `ConfigMap`, `Secret` YAML manifests
- Liveness probe: `/actuator/health/liveness`
- Readiness probe: `/actuator/health/readiness`
- Resource limits and requests
- Horizontal Pod Autoscaler (HPA) configuration

#### [x] 6.6 — Docker Compose (Development)
- `docker-compose.yml` with:
  - PostgreSQL 16
  - RabbitMQ 3.13 (with management plugin)
  - Redis 7
  - FIS application container
- Single `docker compose up` for local development

#### [x] 6.7 — Documentation Finalization
- API documentation (OpenAPI / Swagger)
- Runbook for operations:
  - DLQ message inspection and replay procedure
  - Accounting Period close procedure
  - Exchange rate upload procedure
  - Emergency period reopen procedure
- Architecture decision records (ADRs) for key decisions

#### [x] 6.8 — Testing
- Security tests: Verify RBAC enforcement (reader cannot post, accountant cannot manage rules)
- Security tests: Missing/invalid JWT → `401`
- Security tests: Valid JWT but wrong role → `403`
- Load test results documented and compared against SRS NFRs
- End-to-end smoke test: Event → RabbitMQ → Pipeline → JE → Hash → Outbox → Downstream
- Chaos test: Kill a Postgres connection mid-transaction → verify no partial writes, message requeued

### Acceptance Criteria
- [x] OpenTelemetry traces span the entire pipeline (REST ingress → Postgres commit → Outbox publish)
- [x] W3C Traceparent headers propagate through RabbitMQ messages
- [x] RBAC correctly restricts operations per role
- [x] Load tests prove ≥ 10,000 events/sec sustained throughput
- [x] Load tests prove REST p99 < 200ms
- [x] Hot-account concurrency test passes with zero balance corruption
- [x] Kubernetes deployment works with health probes
- [x] `docker compose up` boots the full local stack
- [x] API documentation (OpenAPI) is generated and accessible

---

## Summary: Requirements Traceability Matrix

This matrix maps every SRS requirement to the phase in which it is implemented.

| Phase | Requirements Covered |
| :---- | :---- |
| **Phase 1** | FR-03, FR-04, FR-05, FR-06, C-01, C-03, C-04, C-06, NFR-17, NFR-23 |
| **Phase 2** | FR-07, FR-08, FR-09, FR-10, FR-12, FR-13, FR-14, NFR-02 (locking) |
| **Phase 3** | FR-31, FR-32, FR-33, FR-34, FR-35, FR-36, FR-37, FR-38, FR-39, NFR-07, NFR-08 |
| **Phase 4** | FR-01, FR-02, FR-11, FR-19, FR-20, FR-21, FR-22, FR-23, FR-24, FR-25, FR-26 |
| **Phase 5** | FR-15, FR-16, FR-17, FR-18, FR-27, FR-28, FR-29, FR-30, FR-40, FR-41 |
| **Phase 6** | NFR-01, NFR-02, NFR-06, NFR-09, NFR-10, NFR-12, NFR-14, NFR-18, NFR-19, NFR-20, NFR-21, NFR-22, NFR-23 |

---

## Risk Register

| ID | Risk | Impact | Mitigation |
| :---- | :---- | :---- | :---- |
| R-01 | Hot account contention under extreme load exceeds PostgreSQL row-lock throughput | JE processing throughput degrades beyond 10K/sec target | Monitor lock wait times in Phase 6 load tests. If critical, evaluate integrating Laminar request coalescing as a future optimization. |
| R-02 | Redis fails or becomes unavailable | Idempotency checks bypass, potential duplicate JEs | Fallback to PostgreSQL `fis_idempotency_log` UPSERT. Deploy Redis with Sentinel or Cluster for HA. |
| R-03 | SpEL expression injection in Mapping Rules | Security vulnerability via malicious rule expressions | Sandbox SpEL evaluation with a restricted `SimpleEvaluationContext`. Disallow method invocation and type references. |
| R-04 | Flyway migration failure in production | Application fails to start | Test all migrations with Testcontainers. Maintain manual rollback scripts. Blue-green deployment strategy. |
| R-05 | RabbitMQ broker goes down | Events pile up in upstream outbox tables | Quorum Queues provide HA. Upstream systems use Transactional Outbox and will retry. Monitor queue depth alerts. |
| R-06 | Hash chain is broken due to concurrent writes | Tamper detection integrity compromised | Serialize hash computation per tenant using `SELECT ... FOR UPDATE` on a tenant-level hash sequence row. |

---

*End of Development Implementation Roadmap*
