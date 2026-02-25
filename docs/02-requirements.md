# Product Requirements Document (PRD): Generic FIS Engine

## 1. Overview & Objectives
**Goal:** Build a loosely-coupled, highly concurrent Financial Information System (FIS) engine capable of ingesting financial events from agnostic external domains and enforcing strict double-entry accounting principles. The system will leverage a modern stack (Java 25, Spring Boot 4.0.1, Gradle) to ensure performance, type-safety, and minimal boilerplate.

## 2. Core Principles
- **Agnosticism:** The engine must know nothing of the business logic that generates the financial events.
- **Null Safety by Default:** Embracing Spring Boot 4.0.1 best practices, the entire project must apply JSpecify annotations (`@NullMarked`) to eliminate runtime `NullPointerException`s.
- **Immutability:** Financial ledgers are append-only. No updates. No deletes.
- **Precision:** All monetary amounts must be stored and processed as `Long` (cents) or `BigDecimal`. No floating-point types.
- **Resilience:** The system must guarantee exactly-once processing for all incoming financial events via Idempotency Keys and RabbitMQ architectures.
- **RFC 7807 Compliance:** All API error responses conform to the Problem Details for HTTP APIs standard.

## 3. Functional Requirements

### 3.1 Multi-Tenancy & Business Entities
- **FR1:** The system must support multiple isolated Business Entities (tenants), each with its own Chart of Accounts, base currency, and fiscal calendar.
- **FR2:** All data access must be scoped by `tenant_id`. Cross-tenant data leakage is a critical security violation.

### 3.2 Account & Chart of Accounts (CoA) Management
- **FR3:** The system must allow administrators to define a hierarchical Chart of Accounts (CoA) as a tree structure (parent-child).
- **FR4:** The system must expose CRUD APIs for creating, reading, updating (name/status only), and deactivating Accounts. Accounts with existing Journal Lines must never be deleted.
- **FR5:** Each Account must be assigned one of the five standard types: `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`.
- **FR6:** The engine must enforce the fundamental accounting equation: `Assets = Liabilities + Equity + Revenue - Expenses`.

### 3.3 Journal Entry Processing & Mapping
- **FR7:** Expose robust, versioned REST APIs (Spring Boot 4.0 API versioning native support) for synchronous intake, alongside RabbitMQ consumers for asynchronous event processing.
- **FR8:** Use **ModelMapper** to securely map incoming generic DTOs into standard double-entry `DraftJournalEntry` domain entities without extensive boilerplate.
- **FR9:** Validate that the sum of all Debits equals the sum of all Credits in a given Journal Entry before persisting to the ledger.
- **FR10:** The system must reject Journal Entries targeting "closed" Accounting Periods.
- **FR11:** Allow arbitrary dimensional tags (e.g., `{"costCenter": "HR", "projectId": "P-100"}`) to be attached to Journal Entry Lines for multi-dimensional reporting.
- **FR12:** Expose query APIs for Journal Entries filtered by date range, account code, reference ID, status, and dimensions.

### 3.4 Journal Entry Reversals & Corrections
- **FR13:** The system must support **Full Reversal** of a posted Journal Entry. This creates a new mirror JE (opposite Debits/Credits) and links it back via `reversal_of_id`. The original JE remains unchanged.
- **FR14:** The system must support **Correction** (Reversal + Re-entry) as a single atomic API call. It reverses the original and posts the replacement entry.
- **FR15:** The system must reject attempts to reverse a Journal Entry that already has a posted reversal entry.

### 3.5 Accounting Period Management
- **FR16:** The system must allow administrators to create and manage Accounting Periods (monthly) per Business Entity.
- **FR17:** Periods must support three states: `OPEN`, `SOFT_CLOSED`, `HARD_CLOSED`.
- **FR18:** Hard closes must be sequential (oldest first). Reopening a period requires reopening all subsequent periods.
- **FR19:** Expose CRUD + state-transition APIs for Accounting Periods (`open`, `soft-close`, `hard-close`, `reopen`).

### 3.6 Multi-Currency Support
- **FR20:** Each Business Entity must declare a `base_currency`. All balances are ultimately reported in this currency.
- **FR21:** Journal Entries must record the `transaction_currency`, `base_currency`, and `exchange_rate` used at the time of posting.
- **FR22:** The system must support storing daily exchange rates per currency pair.
- **FR23:** The system must support Period-End Revaluation — revaluing outstanding foreign currency monetary balances at closing rates, generating unrealized FX gain/loss entries automatically.

### 3.7 Mapping Rules Engine
- **FR24:** Expose CRUD APIs for administrators to configure Mapping Rules that translate external event types into Draft Journal Entries with configurable debit/credit account expressions.
- **FR25:** Mapping rules must be versionable and auditable (who changed what, when).

### 3.8 Event-Driven Reliability (RabbitMQ)
- **FR26:** All intake queues must be configured as **Quorum Queues** for high availability and replication.
- **FR27:** Messages failing structural validation must be routed to a **Dead Letter Queue (DLQ)** for later inspection.
- **FR28:** Consumers must utilize careful Prefetch tuning to balance workload distribution.
- **FR29:** System assumes upstream applications use the **Transactional Outbox Pattern** to prevent dropped financial events.

### 3.9 Idempotency and Fault Tolerance (Redis)
- **FR30:** The system must use `eventId` as the canonical Idempotency Key (`ik`) on all intake mechanisms and validate uniqueness via **Redis** `SET NX EX`.
- **FR31:** Redis key pattern: `fis:ik:{tenant_id}:{event_id}` with 72-hour TTL.
- **FR32:** Duplicate requests carrying a previously seen `eventId` must return the cached response without hitting the ledger.

## 4. Non-Functional Requirements (NFRs)

### 4.1 Performance & Concurrency (Java 25 & Project Loom)
- **NFR1:** The system must support a minimum throughput of 10,000 incoming events per second, natively relying on Java 25 Virtual Threads.
- **NFR2:** The system must utilize PostgreSQL pessimistic locking (`SELECT ... FOR UPDATE`) to handle concurrent writes to hot accounts safely.
- **NFR3:** The deployment pipeline will be managed by **Gradle**.
- **NFR4:** Implementation will use **Lombok** extensively to reduce boilerplate.

### 4.2 Security, Observability & Audit
- **NFR5:** Database schemas storing Journal Entries must implement cryptographic hashing (hash chain).
- **NFR6:** Complete integration of `spring-boot-starter-opentelemetry` for cohesive metrics, traces, and **SLF4J** structured logs passing across the RabbitMQ boundary.
- **NFR7:** Strict Role-Based Access Control (RBAC) separating administrative configuration from manual journal offsets. Roles: `FIS_ADMIN`, `FIS_ACCOUNTANT`, `FIS_READER`.
- **NFR8:** All mapping rule changes must be captured in an immutable audit log.

### 4.3 Error Handling
- **NFR9:** All REST API error responses must comply with **RFC 7807 ProblemDetail** via a global `@ControllerAdvice` exception handler.
- **NFR10:** Standard error codes/types must be defined for: `UNBALANCED_ENTRY`, `PERIOD_CLOSED`, `DUPLICATE_IDEMPOTENCY_KEY`, `ACCOUNT_NOT_FOUND`, `INVALID_REVERSAL`, `VALIDATION_FAILED`.

### 4.4 Schema Evolution
- **NFR11:** All database migrations must be managed by **Flyway** with versioned scripts (`V1__`, `V2__`). Hibernate `ddl-auto` is set to `validate` in production.
- **NFR12:** Migration scripts must be tested with Testcontainers before deployment.

### 4.5 Outbound Communication
- **NFR13:** Any synchronous communication *out* of the FIS must utilize Spring Boot 4.0's declarative `@HttpExchange` interfaces rather than legacy `RestTemplate`.

## 5. Out of Scope
- E-commerce inventory tracking
- Direct integration with payment gateways (Stripe, PayPal)
- User authentication / SSO workflows (handled by API Gateway)
- Budgeting and forecasting
- Tax computation and filing
