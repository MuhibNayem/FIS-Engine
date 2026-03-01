# FIS Process

Production-grade, domain-agnostic Financial Information System (FIS) engine for immutable double-entry accounting.

## Overview

`fis-process` is designed as a multi-tenant financial ledger service that converts upstream business events into accounting entries while preserving strict accounting integrity.

Core principles:
- Append-only ledger semantics (no mutation/deletion of posted journal records).
- Exactly-once posting through `eventId`-based idempotency.
- Strict double-entry enforcement (`debits == credits`).
- Tenant isolation on all read/write paths.
- Auditability via hash-chain and immutable audit events.

## System Scope

In scope:
- Generic ledger posting (manual + event-driven).
- Account and chart-of-accounts management.
- Period controls (`OPEN`, `SOFT_CLOSED`, `HARD_CLOSED`).
- Multi-currency posting (`transaction_currency`, `base_currency`, `exchange_rate`).
- Reversal and correction flows using compensating entries.

Out of scope:
- ERP workflows.
- Payments gateway processing.
- Tax engine and filing.
- Identity/auth provider (assumed upstream gateway/JWT).

## Tech Stack

Current project baseline:
- Java 25
- Spring Boot 4.0.3
- Gradle
- Spring MVC + Validation
- Spring Data JPA / JDBC
- Redis starter
- H2 (runtime) + PostgreSQL driver
- Lombok

Current architecture:
- RabbitMQ Quorum Queues + DLQ
- Redis idempotency layer (`fis:ik:{tenant_id}:{event_id}`)
- PostgreSQL as system-of-record
- Transactional outbox for downstream events
- OpenTelemetry + structured logs

## Architecture Summary

Processing pipeline:
1. Event intake (`REST` or message consumer)
2. Idempotency check (`eventId`)
3. Rule mapping (`eventType` -> draft journal)
4. Period validation
5. Journal validation
6. Currency conversion
7. Account row locking (`SELECT ... FOR UPDATE`, deterministic ordering)
8. ACID persist (JE + lines + balances)
9. Hash-chain update
10. Outbox publish

## Repository Structure

- `docs/` - Product and technical design documents (SRS, API, schema, roadmap).
- `src/main/` - Application source and runtime config.
- `src/test/` - Tests.
- `build.gradle` - Build and dependency management.

## Documentation Index

- `docs/SRS.md` - Authoritative requirement baseline.
- `docs/01-analysis.md` - Conceptual deep analysis.
- `docs/02-requirements.md` - Product requirements.
- `docs/03-architecture.md` - Technical architecture.
- `docs/04-database-schema.md` - DDL and migrations design.
- `docs/05-api-contracts.md` - REST contracts.
- `docs/06-messaging-topology.md` - RabbitMQ + Redis design.
- `docs/07-domain-models.md` - Entities/DTOs/service contracts.
- `docs/08-implementation-roadmap.md` - Phased implementation plan.
- `docs/12-plug-and-play-adoption-guide.md` - Fast integration guide for consuming projects.
- `deploy/k8s/` - Kubernetes manifests (deployment/service/config/secret/HPA).
- `deploy/otel/` - OpenTelemetry Collector config.
- `performance/k6/` - Load/stress test scripts.
- `docs/runbooks/` - Operations runbooks.
- `docs/runbooks/application-runbook.md` - Full production application runbook.

## Getting Started

### Prerequisites

- JDK 25
- Docker (recommended for local infra)
- PostgreSQL 16+ (if not using H2)
- Redis 7+ (for idempotency features)

### Build

```bash
./gradlew clean build
```

### Run

```bash
./gradlew bootRun
```

Default app name:
- `spring.application.name=fis-process`

## Configuration

This project should be configured via environment variables per environment (`dev`, `test`, `prod`).

Recommended configuration domains:
- Database (`spring.datasource.*`)
- JPA/Flyway (`spring.jpa.*`, `spring.flyway.*`)
- Redis (`spring.data.redis.*`)
- RabbitMQ (`spring.rabbitmq.*`)
- Security (`JWT`, tenant header validation)
- Observability (`otel.*`, logging pattern)

## API and Domain Conventions

- Tenant boundary is mandatory: `X-Tenant-Id`.
- Canonical idempotency key is `eventId`.
- RFC 7807 `ProblemDetail` for all error payloads.
- Monetary values are integer cents (`BIGINT`) or fixed precision (`NUMERIC`).
- Posted financial history is append-only; corrections use compensating entries.
- API documentation:
  - OpenAPI spec: `/openapi.yaml`
  - Swagger UI: `/swagger-ui.html`

## JWT Configuration

- `fis.security.jwt.public-key-pem`
  - Required for JWT verification.
  - Uses RSA public key verification.
  - Production should use RS256 tokens issued by upstream IdP.

## Immutability Model (Industry Standard)

- **Strict append-only (immutable):**
  - `fis_journal_entry`
  - `fis_journal_line`
  - Enforced at PostgreSQL DB layer (triggers block `UPDATE`/`DELETE`).

- **Mutable derived state (intentional):**
  - `fis_account.current_balance` is updated transactionally for low-latency reads.
  - Source of truth remains the immutable journal.

## Data Integrity Rules

- Journal entry must be balanced.
- Account must exist and be active.
- Posting period must allow writes.
- One posted reversal per original entry (enforced by unique index on `reversal_of_id` where not null).
- `eventId` uniqueness is tenant-scoped.

## Performance and Scalability Targets

- Sustained ingestion target: `>= 10,000 events/sec`.
- Manual JE posting p99: `< 200 ms`.
- Hot-account safety with deterministic lock ordering and bounded retry/backoff.

## Quality Gates

- Unit tests for all validation and domain invariants.
- Integration tests for posting, reversal, idempotency, and period rules.
- Concurrency tests for hot accounts and deadlock resilience.
- Migration verification in isolated test databases.

## Delivery Model

Implementation is planned in phased milestones:
- Phase 1: Foundation and account APIs.
- Phase 2: Core ledger. ✅ implemented
- Phase 3: Event intake + idempotency. ✅ implemented
- Phase 4: Period + FX.
- Phase 5: Reversal/rules/audit.
- Phase 6: Observability and production hardening.

See `docs/08-implementation-roadmap.md` for details.

## Current Status

Implemented and verified:
- Phase 1 foundation and account APIs
- Phase 2 core ledger engine (manual journal posting/query, hash chain, concurrency safety, append-only ledger enforcement)
- Phase 3 event-driven intake and idempotency (REST `/v1/events`, RabbitMQ consumer with manual ack/nack, Redis + PostgreSQL idempotency log, transactional outbox relay)
- Phase 4 multi-currency + accounting periods
- Phase 5 reversals/corrections + mapping rules + audit log + period-end revaluation
- Phase 6 JWT/RBAC hardening, trace context propagation, deployment manifests, compose stack, and load-test assets

## Contribution Guidelines

- Keep financial invariants explicit and test-covered.
- Do not introduce mutable behavior for posted ledger rows.
- Maintain backward-compatible API evolution (`/v1` contracts).
- Any schema change must be migration-driven and reviewed for audit impact.

## License

Internal project. Distribution and usage follow organizational policy.
