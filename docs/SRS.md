# Software Requirements Specification (SRS)

## Generic Financial Information System (FIS) Engine

---

| Field | Value |
| :---- | :---- |
| **Document Title** | Software Requirements Specification — Generic FIS Engine |
| **Version** | 1.0 |
| **Date** | February 25, 2026 |
| **Prepared By** | Engineering Division |
| **Standard** | IEEE 830-1998 / ISO/IEC/IEEE 29148:2018 |
| **Classification** | Confidential — Internal Use Only |

---

## Revision History

| Version | Date | Author | Description |
| :---- | :---- | :---- | :---- |
| 1.0 | 2026-02-25 | Engineering Division | Initial SRS baseline |

---

## Table of Contents

1. [Introduction](#1-introduction)  
2. [Overall Description](#2-overall-description)  
3. [Specific Requirements](#3-specific-requirements)  
4. [Data Model & Schema](#4-data-model--schema)  
5. [External Interface Requirements](#5-external-interface-requirements)  
6. [Non-Functional Requirements](#6-non-functional-requirements)  
7. [Appendices](#7-appendices)

---

## 1\. Introduction

### 1.1 Purpose

This Software Requirements Specification (SRS) defines the complete functional, non-functional, and technical requirements for the **Generic Financial Information System (FIS) Engine**. This document serves as the authoritative contract between the Engineering Division, Product Management, and Executive Stakeholders for all aspects of system behavior, performance, and quality.

This SRS is intended to be used by:

- Executive leadership and the Board of Directors — for strategic approval and investment oversight.  
- Engineering teams — for implementation guidance.  
- Quality Assurance — for test plan derivation.  
- Compliance & Audit — for regulatory alignment verification.

### 1.2 Product Scope

The FIS Engine is a **standalone, domain-agnostic, event-driven financial ledger service** that functions as the organization's immutable system of record for all financial state transitions. It enforces strict double-entry accounting principles, guarantees idempotent processing, and operates at high throughput using modern concurrency primitives.

**The FIS Engine IS:**

- A generic, multi-tenant, multi-currency double-entry accounting ledger.  
- An event-driven financial state machine that translates business events into Journal Entries.  
- An append-only, tamper-evident ledger with cryptographic hash chains.

**The FIS Engine IS NOT:**

- An ERP system, invoicing platform, or billing engine.  
- A payment gateway or processor.  
- A budgeting, forecasting, or tax computation system.  
- A user authentication or identity management service.

### 1.3 Definitions, Acronyms, and Abbreviations

| Term | Definition |
| :---- | :---- |
| **FIS** | Financial Information System |
| **Double-Entry Accounting** | A bookkeeping method where every financial transaction is recorded in at least two accounts — one Debit and one Credit — ensuring the accounting equation always balances. |
| **Journal Entry (JE)** | An atomic unit of financial work consisting of one or more Journal Lines. Each JE is immutable once posted. |
| **Journal Line** | A single Debit or Credit instruction within a Journal Entry, targeting a specific Account. |
| **Chart of Accounts (CoA)** | The hierarchical tree of all accounts maintained by a Business Entity. |
| **Business Entity** | An isolated organizational unit (company, subsidiary, division) operating within the FIS with its own CoA, base currency, and fiscal calendar. Synonymous with "Tenant." |
| **Accounting Period** | A defined time window (typically a calendar month) during which Journal Entries may be posted. Periods transition through OPEN → SOFT\_CLOSED → HARD\_CLOSED states. |
| **Idempotency Key (ik)** | `eventId`, a globally unique identifier attached to every financial event or write API request, ensuring exactly-once processing. |
| **Mapping Rule** | An admin-configurable translation directive that specifies how a given external event type maps to Debit/Credit account entries. |
| **Hot Account** | An account (e.g., Treasury, Platform Fees) that receives an abnormally high volume of concurrent write operations. |
| **CQRS** | Command Query Responsibility Segregation — a pattern separating read and write data paths. |
| **DLQ** | Dead Letter Queue — a message queue that stores messages that cannot be processed successfully. |
| **RFC 7807** | IETF standard "Problem Details for HTTP APIs" — defines a machine-readable error response format. |
| **Quorum Queue** | A RabbitMQ queue type that replicates messages across multiple broker nodes using RAFT consensus for high availability. |
| **Virtual Threads** | Lightweight threads in Java 25 (Project Loom) that allow millions of concurrent tasks without proportional OS thread consumption. |

### 1.4 References

| ID | Document | Source |
| :---- | :---- | :---- |
| REF-01 | Deep Analysis — FIS Engine | `docs/01-analysis.md` |
| REF-02 | Product Requirements Document — FIS Engine | `docs/02-requirements.md` |
| REF-03 | Technical Architecture Document — FIS Engine | `docs/03-architecture.md` |
| REF-04 | Database Schema (DDL) — FIS Engine | `docs/04-database-schema.md` |
| REF-05 | API Contracts (REST/JSON) — FIS Engine | `docs/05-api-contracts.md` |
| REF-06 | Messaging Topology (RabbitMQ) & Redis — FIS Engine | `docs/06-messaging-topology.md` |
| REF-07 | Domain Entities, DTOs & Service Interfaces — FIS Engine | `docs/07-domain-models.md` |
| REF-08 | IEEE 830-1998 — Recommended Practice for SRS | IEEE |
| REF-09 | ISO/IEC/IEEE 29148:2018 — Requirements Engineering | ISO |
| REF-10 | RFC 7807 — Problem Details for HTTP APIs | IETF |

### 1.5 Document Conventions

- **SHALL / MUST:** Indicates a mandatory requirement. Non-compliance constitutes a defect.  
- **SHOULD:** Indicates a strongly recommended practice. Deviation requires documented justification.  
- **MAY:** Indicates an optional capability.  
- Requirements are uniquely identified by prefix: `FR-` (Functional), `NFR-` (Non-Functional), `IR-` (Interface).  
- All monetary values referenced in this document are expressed in **cents** (integer representation) unless explicitly stated otherwise.

---

## 2\. Overall Description

### 2.1 Product Perspective

The FIS Engine operates as a **shared infrastructure service** within the enterprise's microservices ecosystem. It is positioned below all domain-specific operational systems (E-commerce, Payroll, Lending, SaaS Billing, etc.) and provides them with a single, consistent, mathematically rigorous ledger.

**System Context:**

┌──────────────────────────────────────────────────────────────────────┐  
│                    ENTERPRISE ECOSYSTEM                              │  
│                                                                      │  
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐      │  
│  │E-Commerce│  │ Payroll  │  │ Lending  │  │ SaaS Billing     │      │  
│  │ Service  │  │ Service  │  │ Service  │  │ Service          │      │  
│  └────┬─────┘  └─────┬────┘  └────┬─────┘  └────────┬─────────┘      │  
│       │              │            │                 │                │  
│       ▼              ▼            ▼                 ▼                │  
│  ┌───────────────────────────────────────────────────────────────┐   │  
│  │                      RabbitMQ Broker                          │   │  
│  │              (Quorum Queues \+ DLQ Topology)                   │   │  
│  └───────────────────────────┬───────────────────────────────────┘   │  
│                              │                                       │  
│                              ▼                                       │  
│  ┌───────────────────────────────────────────────────────────────┐   │  
│  │                                                               │   │  
│  │              GENERIC FIS ENGINE (This System)                 │   │  
│  │                                                               │   │  
│  │  ┌─────────┐  ┌──────────┐  ┌───────────┐  ┌─────────────┐    │   │  
│  │  │  REST   │  │ RabbitMQ │  │  Service  │  │  PostgreSQL │    │   │  
│  │  │  APIs   │  │ Consumer │  │  Pipeline │  │  Ledger DB  │    │   │  
│  │  └─────────┘  └──────────┘  └───────────┘  └─────────────┘    │   │  
│  │  ┌─────────┐  ┌──────────────────────────────────────────┐    │   │  
│  │  │  Redis  │  │  Downstream Event Publishing (Outbox)    │    │   │  
│  │  └─────────┘  └──────────────────────────────────────────┘    │   │  
│  │                                                               │   │  
│  └───────────────────────────────────────────────────────────────┘   │  
│                                                                      │  
│  ┌───────────────────────────────────────────────────────────────┐   │  
│  │              Downstream Consumers                             │   │  
│  │  (Reporting, Analytics, Reconciliation, Audit)                │   │  
│  └───────────────────────────────────────────────────────────────┘   │  
└──────────────────────────────────────────────────────────────────────┘

The FIS Engine has **zero knowledge** of the business logic of upstream services. It operates exclusively on generic financial primitives: Accounts, Journal Entries, Balances, and Mapping Rules.

### 2.2 Product Functions — Executive Summary

| Capability | Description |
| :---- | :---- |
| **Multi-Tenant Ledger** | Isolated financial ledgers per Business Entity, each with its own Chart of Accounts, base currency, and fiscal periods. |
| **Event-Driven Intake** | Asynchronous ingestion of financial events from upstream systems via RabbitMQ, with synchronous REST APIs for manual adjustments. |
| **Configurable Rules Engine** | Admin-configurable Mapping Rules translate opaque business events into double-entry Journal Entries without code changes. |
| **Strict Double-Entry Enforcement** | Every Journal Entry is validated to ensure Debits \= Credits before persistence. The fundamental accounting equation is an invariant. |
| **Multi-Currency Accounting** | Native support for transactions in any currency, with automatic base-currency conversion and FX gain/loss tracking. |
| **Accounting Period Control** | Fiscal period lifecycle management (OPEN → SOFT\_CLOSED → HARD\_CLOSED) to control when entries may be posted. |
| **Immutable Append-Only Ledger** | No updates or deletions. Errors are corrected through reversing entries. Tamper detection via cryptographic hash chains (SHA-256). |
| **Exactly-Once Processing** | Idempotency guaranteed by Redis (primary) \+ PostgreSQL (durable fallback), ensuring no duplicate financial postings. |
| **Journal Entry Reversals** | Full reversal and correction (reverse \+ re-entry) operations preserving complete audit trail. |
| **High-Throughput Concurrency** | Java 25 Virtual Threads handle massive concurrent event processing. PostgreSQL pessimistic locking (`SELECT FOR UPDATE`) manages hot-account contention. |

### 2.3 User Classes and Characteristics

| User Class | Role Code | Description | Primary Actions |
| :---- | :---- | :---- | :---- |
| **System Administrator** | `FIS_ADMIN` | Operations/DevOps personnel responsible for platform configuration. | Manage tenants, configure mapping rules, manage accounting periods, upload exchange rates. |
| **Accountant / Controller** | `FIS_ACCOUNTANT` | Finance team members responsible for financial accuracy. | Post manual journal entries, reverse entries, close/reopen periods, view balances. |
| **Auditor / Reader** | `FIS_READER` | Internal or external auditors who require read-only access. | View journal entries, view account balances, view audit logs. Read-only access. |
| **Upstream System** | N/A (Machine) | Domain services that publish financial events via RabbitMQ or REST. | Submit financial events. No direct access to ledger. |
| **Downstream Consumer** | N/A (Machine) | Reporting, analytics, or reconciliation systems that subscribe to domain events. | Consume `journal.posted` / `journal.reverted` events. |

### 2.4 Operating Environment

| Component | Specification |
| :---- | :---- |
| **Runtime** | Java 25 (LTS) with Virtual Threads enabled |
| **Framework** | Spring Boot 4.0.1 (Spring Framework 7\) |
| **Build System** | Gradle |
| **Application Server** | Embedded Tomcat (Spring Boot managed) |
| **Database** | PostgreSQL 16+ with JSONB support |
| **Message Broker** | RabbitMQ 3.13+ with Quorum Queue support |
| **Cache / Idempotency Store** | Redis 7+ |
| **Container Runtime** | Docker / Kubernetes (OCI-compliant) |
| **Observability** | OpenTelemetry (spring-boot-starter-opentelemetry) \+ SLF4J |
| **Schema Migration** | Flyway 10+ |

### 2.5 Design and Implementation Constraints

| ID | Constraint | Rationale |
| :---- | :---- | :---- |
| C-01 | All monetary values SHALL be stored as `BIGINT` (cents) or `NUMERIC` in the database. Floating-point types (`FLOAT`, `DOUBLE`) are prohibited. | Floating-point arithmetic introduces rounding errors unacceptable in financial systems. |
| C-02 | The ledger SHALL be strictly append-only. `UPDATE` and `DELETE` operations on `fis_journal_entry` and `fis_journal_line` tables are prohibited at the application and database levels. | Immutability is essential for audit compliance and tamper detection. |
| C-03 | All packages SHALL be annotated with JSpecify `@NullMarked`. | Eliminates runtime NullPointerExceptions and enforces type-safe null contracts. |
| C-04 | Hibernate `ddl-auto` SHALL be set to `validate` in production. Schema evolution is managed exclusively by Flyway. | Prevents uncontrolled schema mutations in production. |
| C-05 | All API error responses SHALL conform to RFC 7807 (Problem Details for HTTP APIs). | Provides machine-readable, standardized error responses for API consumers. |
| C-06 | Jakarta EE 11 namespaces (`jakarta.*`) SHALL be used exclusively. | Spring Boot 4.0 requires Jakarta EE 11 alignment. |

### 2.6 Assumptions and Dependencies

| ID | Assumption / Dependency |
| :---- | :---- |
| A-01 | Upstream systems sending financial events implement the **Transactional Outbox Pattern** to guarantee event delivery to RabbitMQ. |
| A-02 | User authentication and authorization are handled by an external **API Gateway**. The FIS receives pre-authenticated JWTs with role claims. |
| A-03 | Exchange rates are provided by an external service or manual upload. The FIS does not source rates from market data feeds. |
| A-04 | PostgreSQL, RabbitMQ, and Redis are deployed as managed services with high availability, backups, and monitoring. |
| A-05 | Network latency between the FIS application and its data stores (PostgreSQL, Redis) is \< 2ms within the same VPC. |

---

## 3\. Specific Requirements

### 3.1 Functional Requirements — Multi-Tenancy & Business Entities

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-01 | The system SHALL support multiple isolated Business Entities (tenants), each with its own Chart of Accounts, base currency, and fiscal calendar. | Critical |
| FR-02 | All data access SHALL be scoped by `tenant_id`. Cross-tenant data leakage SHALL be treated as a critical security vulnerability. | Critical |

### 3.2 Functional Requirements — Account & Chart of Accounts Management

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-03 | The system SHALL allow administrators to define a hierarchical Chart of Accounts (CoA) as a tree structure (parent-child relationships). | Critical |
| FR-04 | The system SHALL expose CRUD APIs for creating, reading, updating (name and status only), and deactivating Accounts. | Critical |
| FR-05 | Accounts with existing Journal Lines SHALL NOT be deleted. Deactivation (soft delete) is the only permitted removal mechanism. | Critical |
| FR-06 | Each Account SHALL be assigned one of the five standard types: `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`. | Critical |
| FR-07 | The engine SHALL enforce the fundamental accounting equation: `Assets = Liabilities + Equity + Revenue - Expenses`. | Critical |
| FR-08 | Each Account SHALL maintain an atomic `current_balance` field (in cents), updated transactionally with every posted Journal Entry. | Critical |

### 3.3 Functional Requirements — Journal Entry Processing

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-09 | The system SHALL expose REST APIs for synchronous Journal Entry intake and RabbitMQ consumers for asynchronous event processing. | Critical |
| FR-10 | The system SHALL validate that the sum of all Debits equals the sum of all Credits in a given Journal Entry before persisting. Any imbalance SHALL be rejected. | Critical |
| FR-11 | The system SHALL reject Journal Entries targeting a `SOFT_CLOSED` or `HARD_CLOSED` Accounting Period (unless the user has elevated privileges for `SOFT_CLOSED` periods). | Critical |
| FR-12 | The system SHALL allow arbitrary dimensional tags (JSONB key-value pairs) to be attached to Journal Lines for multi-dimensional reporting and filtering. | High |
| FR-13 | The system SHALL expose query APIs for Journal Entries filtered by date range, account code, reference ID, status, and dimensions, with pagination support. | High |
| FR-14 | Every posted Journal Entry SHALL be assigned a SHA-256 cryptographic hash computed from its own data concatenated with the hash of the preceding entry, forming a tamper-evident hash chain. | Critical |

### 3.4 Functional Requirements — Journal Entry Reversals & Corrections

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-15 | The system SHALL support **Full Reversal** of a posted Journal Entry by creating a new mirror entry with opposite Debits/Credits. The original Journal Entry SHALL remain unchanged. | Critical |
| FR-16 | The reversal entry SHALL link back to the original via a `reversal_of_id` field. | Critical |
| FR-17 | The system SHALL support **Correction** (reverse \+ re-entry) as a single atomic API call. | High |
| FR-18 | The system SHALL reject attempts to reverse a Journal Entry that already has a posted reversal entry. | Critical |

### 3.5 Functional Requirements — Accounting Period Management

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-19 | The system SHALL allow administrators to create and manage Accounting Periods (monthly boundaries) per Business Entity. | Critical |
| FR-20 | Each Accounting Period SHALL support three states: `OPEN`, `SOFT_CLOSED`, `HARD_CLOSED`. | Critical |
| FR-21 | Hard closes SHALL be performed sequentially (oldest open period first). Reopening a period SHALL require reopening all subsequent periods first. | Critical |
| FR-22 | The system SHALL expose APIs for state transitions: `open`, `soft-close`, `hard-close`, `reopen`. | High |

### 3.6 Functional Requirements — Multi-Currency Support

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-23 | Each Business Entity SHALL declare a `base_currency` (e.g., `USD`). All balances SHALL be ultimately reportable in this currency. | Critical |
| FR-24 | Every Journal Entry SHALL permanently record the `transaction_currency`, `base_currency`, and `exchange_rate` applied at posting time. These values are immutable. | Critical |
| FR-25 | Every Journal Line SHALL store both `amount` (in transaction currency) and `base_amount` (converted to base currency). | Critical |
| FR-26 | The system SHALL support storing daily exchange rates per currency pair via an admin upload API. | High |
| FR-27 | The system SHOULD support Period-End Revaluation — revaluing outstanding foreign currency monetary balances at closing rates, generating unrealized FX gain/loss entries automatically. | Medium |

### 3.7 Functional Requirements — Mapping Rules Engine

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-28 | The system SHALL expose CRUD APIs for administrators to configure Mapping Rules that translate external event types into Draft Journal Entries. | Critical |
| FR-29 | Each Mapping Rule SHALL define a set of Rule Lines specifying: account code expression, debit/credit designation, and amount expression referencing event payload fields. | Critical |
| FR-30 | Mapping Rules SHALL be versionable. Every update increments the version counter and is logged to the audit trail. | High |

### 3.8 Functional Requirements — Event-Driven Reliability

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-31 | All intake queues SHALL be configured as RabbitMQ **Quorum Queues** for high availability and RAFT-based replication. | Critical |
| FR-32 | Messages failing structural validation SHALL be routed to a **Dead Letter Queue (DLQ)** for manual inspection with no re-queuing on the main queue. | Critical |
| FR-33 | Consumer acknowledgment (`basicAck`) SHALL occur only after the full processing pipeline completes, including database commit. | Critical |
| FR-34 | After a Journal Entry is successfully committed, the system SHALL publish a domain event (e.g., `fis.journal.posted`) for downstream consumers using the Transactional Outbox pattern. | High |

### 3.9 Functional Requirements — Idempotency

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-35 | Every event payload and ledger-writing API request (`/v1/events`, `/v1/journal-entries`, reversal/correction endpoints) SHALL include `eventId` as the unique Idempotency Key (`ik`). | Critical |
| FR-36 | The system SHALL check `eventId` uniqueness against Redis using atomic `SET NX EX` before processing. | Critical |
| FR-37 | The Redis key SHALL follow the pattern: `fis:ik:{tenant_id}:{event_id}` with a TTL of 72 hours. | Critical |
| FR-38 | Duplicate requests carrying a previously-seen `eventId` with a matching payload hash SHALL return the cached response. If the payload hash differs, the system SHALL reject with HTTP 409 Conflict. | Critical |
| FR-39 | The durable idempotency record SHALL also be persisted to PostgreSQL (`fis_idempotency_log`) as a fallback. | High |

### 3.10 Functional Requirements — Audit Trail

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| FR-40 | All administrative configuration changes (mapping rules, accounts, periods) SHALL be logged to an immutable `fis_audit_log` table. | Critical |
| FR-41 | Each audit log entry SHALL record: tenant, entity type, entity ID, action, old value, new value, performed by, and timestamp. | Critical |

---

## 

## 

## 4\. Data Model & Schema

### 4.1 Entity Relationship Overview

┌─────────────────┐       ┌───────────────────┐  
│ BusinessEntity  │◄──────│  AccountingPeriod │  
│   (Tenant)      │       │ OPEN/SOFT/HARD    │  
└────────┬────────┘       └───────────────────┘  
         │  
   ┌─────┴─────────────────────┬──────────────────────┐  
   │                           │                      │  
   ▼                           ▼                      ▼  
┌─────────┐            ┌──────────────┐        ┌──────────────┐  
│ Account │            │ MappingRule  │        │ ExchangeRate │  
│  (CoA)  │            │  \+ RuleLines │        │  (Daily FX)  │  
└───┬─────┘            └──────────────┘        └──────────────┘  
    │  
    │  ┌──────────────────┐  
    │  │  JournalEntry    │◄─── IdempotencyLog  
    │  │  (Immutable)     │  
    │  └────────┬─────────┘  
    │           │  
    ▼           ▼  
┌────────────────────────┐  
│     JournalLine        │  
│ (Debit or Credit line) │  
│  \+ Dimensions (JSONB)  │  
└────────────────────────┘

### 4.2 Database Tables

The complete DDL is defined in REF-04. The following tables are provisioned via Flyway versioned migrations:

| Migration | Table | Purpose |
| :---- | :---- | :---- |
| V1 | `fis_business_entity` | Multi-tenant organizational isolation |
| V2 | `fis_account` | Chart of Accounts — hierarchical account tree with atomic balances |
| V3 | `fis_accounting_period` | Fiscal period lifecycle (OPEN, SOFT\_CLOSED, HARD\_CLOSED) |
| V4 | `fis_exchange_rate` | Daily FX rate snapshots per currency pair |
| V5 | `fis_idempotency_log` | Durable exactly-once processing records |
| V6 | `fis_journal_entry` | Immutable ledger header with hash chain, multi-currency fields, reversal links |
| V7 | `fis_journal_line` | Individual Debit/Credit lines with JSONB dimensions |
| V8 | `fis_mapping_rule` \+ `fis_mapping_rule_line` | Configurable event-to-journal translation rules |
| V9 | `fis_audit_log` | Immutable administrative action log |
| V10 | `fis_outbox` | Transactional outbox for durable domain-event publication |

---

## 5\. External Interface Requirements

### 5.1 User Interfaces

The FIS Engine does not provide a graphical user interface. All interaction occurs through the REST API and messaging interfaces defined below.

### 5.2 Software Interfaces

#### 5.2.1 REST API (Synchronous)

| IR-ID | Endpoint | Method | Description |
| :---- | :---- | :---- | :---- |
| IR-01 | `/v1/events` | POST | Ingest a financial event from an upstream system |
| IR-02 | `/v1/journal-entries` | POST | Post a manual Journal Entry |
| IR-03 | `/v1/journal-entries` | GET | Query journal entries (paginated, filtered) |
| IR-04 | `/v1/journal-entries/{id}/reverse` | POST | Fully reverse a posted Journal Entry |
| IR-05 | `/v1/accounts` | POST | Create a new Account |
| IR-06 | `/v1/accounts` | GET | List accounts (paginated, filtered) |
| IR-07 | `/v1/accounts/{code}` | GET | Get account details and balance |
| IR-08 | `/v1/accounts/{code}` | PATCH | Update account name or deactivate |
| IR-09 | `/v1/accounting-periods` | POST | Create an Accounting Period |
| IR-10 | `/v1/accounting-periods` | GET | List accounting periods |
| IR-11 | `/v1/accounting-periods/{id}/status` | PATCH | Transition period state (open/close/reopen) |
| IR-12 | `/v1/mapping-rules` | POST | Create a Mapping Rule |
| IR-13 | `/v1/mapping-rules` | GET | List mapping rules |
| IR-14 | `/v1/mapping-rules/{id}` | PUT | Update a Mapping Rule |
| IR-15 | `/v1/mapping-rules/{id}` | DELETE | Deactivate a Mapping Rule (soft delete) |
| IR-16 | `/v1/exchange-rates` | POST | Upload daily exchange rates |
| IR-17 | `/v1/exchange-rates` | GET | Query exchange rates |

**Common Request Headers (all endpoints):**

- `Content-Type: application/json`  
- `Authorization: Bearer {jwt_token}`  
- `X-Tenant-Id: {tenant_uuid}` — Required. Scopes all operations to a Business Entity.

Complete request/response schemas are defined in REF-05.

#### 5.2.2 Messaging Interface (Asynchronous)

| IR-ID | Component | Type | Details |
| :---- | :---- | :---- | :---- |
| IR-18 | `fis.events.exchange` | Topic Exchange | Receives all upstream financial events |
| IR-19 | `fis.ingestion.queue` | Quorum Queue | Primary intake queue bound with wildcard `*.*.*` |
| IR-20 | `fis.dlx.exchange` | Direct Exchange | Dead letter routing |
| IR-21 | `fis.ingestion.dlq.queue` | Quorum Queue | Stores failed/poison messages |
| IR-22 | `fis.domain.exchange` | Topic Exchange | Publishes FIS domain events (e.g., `fis.journal.posted`) |

Complete topology is defined in REF-06.

#### 5.2.3 Redis Interface

| IR-ID | Purpose | Key Pattern | TTL |
| :---- | :---- | :---- | :---- |
| IR-23 | Idempotency Check | `fis:ik:{tenant_id}:{event_id}` | 72 hours |

#### 5.2.4 Error Response Interface (RFC 7807\)

All error responses SHALL conform to the following structure:

{

  "type": "/problems/{error-type}",

  "title": "Human-Readable Title",

  "status": 422,

  "detail": "Specific description of this error occurrence.",

  "instance": "/v1/journal-entries"

}

**Standard Error Types:**

| HTTP | Type URI | Title |
| :---- | :---- | :---- |
| 400 | `/problems/validation-failed` | Validation Failed |
| 404 | `/problems/account-not-found` | Account Not Found |
| 409 | `/problems/duplicate-idempotency-key` | Duplicate Idempotency Key |
| 409 | `/problems/invalid-reversal` | Invalid Reversal |
| 422 | `/problems/unbalanced-entry` | Unbalanced Journal Entry |
| 422 | `/problems/period-closed` | Accounting Period Closed |
| 500 | `/problems/internal-error` | Internal Server Error |

---

## 6\. Non-Functional Requirements

### 6.1 Performance & Scalability

| ID | Requirement | Target | Priority |
| :---- | :---- | :---- | :---- |
| NFR-01 | Event ingestion throughput | ≥ 10,000 events/second sustained | Critical |
| NFR-02 | REST API p99 latency (manual JE posting) | \< 200ms | Critical |
| NFR-03 | Redis idempotency check latency (p99) | \< 5ms | Critical |
| NFR-04 | Database query response (JE lookup by date range, 10K results) | \< 500ms | High |
| NFR-05 | Virtual Thread concurrency ceiling | ≥ 100,000 concurrent Virtual Threads | High |
| NFR-24 | Hot-account contention control | Deterministic lock ordering + bounded retry/backoff documented and load-tested | High |

### 6.2 Reliability & Availability

| ID | Requirement | Target | Priority |
| :---- | :---- | :---- | :---- |
| NFR-06 | System uptime SLA | 99.95% (≤ 4.38 hours downtime/year) | Critical |
| NFR-07 | Zero data loss guarantee | No financial event may be silently dropped. All failures must route to DLQ or trigger a retry. | Critical |
| NFR-08 | RabbitMQ message durability | All messages persisted (delivery mode 2\) on Quorum Queues with RAFT replication. | Critical |

### 6.3 Security

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| NFR-09 | Role-Based Access Control with three roles: `FIS_ADMIN`, `FIS_ACCOUNTANT`, `FIS_READER`. | Critical |
| NFR-10 | All inter-service communication encrypted via TLS 1.3. | Critical |
| NFR-11 | All infrastructure (RabbitMQ, Redis, PostgreSQL) deployed within a secure VPC with no public internet exposure. | Critical |
| NFR-12 | JWT token validation on every API request. | Critical |

### 6.4 Auditability & Compliance

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| NFR-13 | Every Journal Entry SHALL include a cryptographic hash chain for tamper detection. | Critical |
| NFR-14 | All API requests SHALL be traceable via OpenTelemetry trace IDs propagated across RabbitMQ and REST boundaries. | Critical |
| NFR-15 | All administrative changes SHALL be logged to `fis_audit_log` with actor identity and timestamps. | Critical |
| NFR-16 | Flyway migration scripts SHALL be immutable once deployed. Modifications require a new migration version. | Critical |

### 6.5 Maintainability

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| NFR-17 | All code SHALL use Lombok (`@Data`, `@Builder`, `@Value`, `@NoArgsConstructor`) for boilerplate reduction. | High |
| NFR-18 | All packages SHALL be annotated with JSpecify `@NullMarked` for compile-time null safety. | Critical |
| NFR-19 | ModelMapper SHALL be used at all DTO-to-Entity boundaries. No manual field-by-field mapping. | High |
| NFR-20 | All outbound HTTP calls SHALL use Spring Boot 4's declarative `@HttpExchange` interfaces. | High |

### 6.6 Observability

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| NFR-21 | The system SHALL integrate `spring-boot-starter-opentelemetry` for unified metrics, traces, and structured SLF4J logs. | Critical |
| NFR-22 | W3C Traceparent headers SHALL be propagated across RabbitMQ message boundaries. | High |
| NFR-23 | Health check endpoints (`/actuator/health`) SHALL be exposed for Kubernetes liveness/readiness probes. | Critical |

---

## 7\. Appendices

### Appendix A — Internal Processing Pipeline

Every incoming financial event traverses the following ordered pipeline:

| Step | Service | Responsibility |
| :---- | :---- | :---- |
| 1 | `EventIntakeService` | Receives REST payload or RabbitMQ message. Extracts headers. |
| 2 | `IdempotencyService` | Redis `SET NX EX`. Rejects duplicates. Returns cached response if exists. |
| 3 | `RuleMappingService` | Loads mapping rule by `eventType`. Uses ModelMapper to produce `DraftJournalEntry`. |
| 4 | `PeriodValidationService` | Checks if the target `postedDate` falls within an `OPEN` Accounting Period. |
| 5 | `JournalEntryValidationService` | Validates `Sum(Debits) == Sum(Credits)`. Validates account codes exist and are active. |
| 6 | `MultiCurrencyService` | Applies exchange rate, computes base-currency amounts for each Journal Line. |
| 7 | `LedgerLockingService` | Acquires `SELECT ... FOR UPDATE` on target account rows. |
| 8 | `LedgerPersistenceService` | ACID transaction: inserts `journal_entry` \+ `journal_lines`, updates `account.current_balance`. |
| 9 | `HashChainService` | Computes SHA-256 hash and stores it on the Journal Entry. |
| 10 | `EventOutboxService` | Writes domain event to outbox table within the same transaction. |

### Appendix B — Technology Stack Summary

| Layer | Technology | Version | Justification |
| :---- | :---- | :---- | :---- |
| Language | Java | 25 (LTS) | Virtual Threads, Compact Object Headers, Scoped Values |
| Framework | Spring Boot | 4.0.1 | Jakarta EE 11, modular starters, OpenTelemetry native |
| Build | Gradle | Latest | Industry-standard JVM build tool |
| Database | PostgreSQL | 16+ | JSONB, advanced indexing, transactional DDL |
| Message Broker | RabbitMQ | 3.13+ | Quorum Queues, DLX, manual ack |
| Cache | Redis | 7+ | Sub-ms idempotency checks, `volatile-ttl` eviction |
| Schema Migration | Flyway | 10+ | Versioned, immutable, Testcontainers-verified |
| Mapping | ModelMapper | Latest | DTO ↔ Entity conversion |
| Boilerplate | Lombok | Latest | `@Data`, `@Builder`, `@Value` |
| Null Safety | JSpecify | Latest | `@NullMarked` on all packages |
| Observability | OpenTelemetry | Spring Boot native | Metrics, traces, structured logs |
| Logging | SLF4J | Spring Boot managed | Structured logging facade |

### Appendix C — Glossary Cross-Reference

See Section 1.3 for the complete glossary.

---

*End of Software Requirements Specification*  
