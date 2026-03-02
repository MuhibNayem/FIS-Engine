# FIS-Engine

Financial ledger platform for high-integrity, multi-tenant accounting workflows.

## 1. Executive Summary
FIS-Engine is a headless backend service that converts business events and manual accounting input into immutable, auditable journal records.  
It is designed for product teams that need strong accounting controls without building ledger infrastructure from scratch.

Core design intent:
- Preserve financial integrity under concurrency and failure.
- Keep posted history immutable (append-only ledger).
- Enforce tenant isolation by default.
- Expose integration-friendly APIs and operational controls.

## 2. Current Scope (v0.0.1-SNAPSHOT)
Implemented:
- Double-entry journal posting and query APIs.
- Approval workflow (draft, submit, approve, reject) with maker-checker enforcement.
- Reversal and correction flows using compensating entries.
- Accounting periods and period controls.
- Multi-currency posting and period-end revaluation.
- Realized FX settlement adjustments.
- Year-end close endpoint.
- Reporting APIs (trial balance, balance sheet, income statement, ledger/risk reports).
- Hierarchy-aware reporting lines for Trial Balance, Balance Sheet, and Income Statement
  (N-level CoA metadata + rolled-up analysis fields).
- Redis-backed distributed rate limiting on high-risk posting paths.
- Transactional outbox for reliable downstream publication.
- OpenTelemetry/Prometheus instrumentation and production runbooks.

Not yet implemented in this repo:
- Multi-ledger per tenant (`N` separate CoAs per tenant) from `docs/multi-ledger-implementation-plan.md`.

## 3. Architecture at a Glance

### Runtime Components
- API: Spring Boot (Java 25), stateless REST.
- Database: PostgreSQL (system of record).
- Cache/coordination: Redis (idempotency and distributed controls).
- Messaging: RabbitMQ (event ingestion + outbox delivery).
- Telemetry: OpenTelemetry + Prometheus metrics.

### Processing Flow
1. Request/event intake.
2. Tenant and security validation.
3. Idempotency check.
4. Domain validation (balance, period, account, policy).
5. Deterministic lock ordering for account updates.
6. ACID persistence (journal entry + lines + balances).
7. Hash-chain update.
8. Outbox publish relay.

## 4. Financial Integrity Guarantees
- Double-entry invariant: total debits must equal total credits.
- Append-only ledger: posted journal tables reject `UPDATE` and `DELETE`.
- Compensating correction model: reversals/corrections instead of mutation.
- Tenant-scoped processing and data access.
- Sequential journal numbering by tenant/fiscal year.
- Idempotent posting pipeline (Redis + durable fallback paths).

## 5. Security Model
- JWT bearer authentication with RSA public key verification.
- Role-based access control (`FIS_ADMIN`, `FIS_ACCOUNTANT`, `FIS_READER`).
- Mandatory tenant context (`X-Tenant-Id`) on business APIs.
- Tenant/JWT context binding support.
- Production guardrails preventing insecure startup configuration.
- Explicit CORS configuration allow-list.

See:
- `docs/05-api-contracts.md`
- `docs/runbooks/application-runbook.md`

## 6. Performance and Reliability
- Concurrency-safe posting with deterministic account lock ordering.
- Bounded retries and backoff in external dependency paths.
- Circuit breaker integration for external boundary protection.
- Graceful shutdown configuration.
- Outbox lag and retry metrics for operational visibility.

## 7. Technology Stack
- Java 25
- Spring Boot 4.0.3
- Spring MVC / Validation / Security / Actuator
- Spring Data JPA + JDBC
- Flyway
- PostgreSQL, Redis, RabbitMQ
- OpenTelemetry + Micrometer/Prometheus
- Gradle

## 8. Repository Layout
- `src/main/java/` application source
- `src/main/resources/` configuration, migrations, static OpenAPI
- `src/test/java/` unit/integration/contract tests
- `docs/` architecture, requirements, contracts, plans, runbooks
- `deploy/` Kubernetes and telemetry deployment assets
- `performance/` load and performance test assets

## 9. Documentation Index
- `docs/SRS.md` - requirements baseline.
- `docs/01-analysis.md` - domain and system analysis.
- `docs/02-requirements.md` - functional/non-functional requirements.
- `docs/03-architecture.md` - architecture and design.
- `docs/04-database-schema.md` - schema and migration model.
- `docs/05-api-contracts.md` - REST contracts.
- `docs/06-messaging-topology.md` - messaging and idempotency topology.
- `docs/07-domain-models.md` - entities/DTOs/contracts.
- `docs/08-implementation-roadmap.md` - phased roadmap.
- `docs/10-audit-remediation-plan.md` - remediation execution log.
- `docs/12-plug-and-play-adoption-guide.md` - integration adoption guide.
- `docs/runbooks/application-runbook.md` - production operations runbook.

## 10. Getting Started

### Prerequisites
- JDK 25
- Docker (recommended)
- PostgreSQL 16+
- Redis 7+
- RabbitMQ 3.13+

### Build
```bash
./gradlew clean build
```

### Run Tests
```bash
./gradlew test check
```

### Start Local Stack
```bash
docker compose up --build -d
```

### Run Service (local JVM)
```bash
./gradlew bootRun
```

## 11. API Access
- OpenAPI spec: `/openapi.yaml`
- Swagger UI: `/swagger-ui.html`
- Base path: `/v1`

Mandatory headers for business APIs:
- `X-Tenant-Id`
- `Authorization: Bearer <jwt>` (when security enabled)

## 12. Configuration Principles
Configuration is environment-driven (dev/test/prod) via `application.yml` and env vars.

High-impact domains:
- Database and pool timeouts
- Redis/Rabbit connectivity and timeouts
- Security (`FIS_SECURITY_ENABLED`, JWT key/claim settings)
- Rate limiting (`fis.rate-limit.*`)
- Resilience/circuit-breaker policies
- Telemetry exporters and sampling

## 13. Quality and Verification
The project uses layered verification:
- Unit tests for domain and service behavior.
- Integration tests with Testcontainers (database, Redis, RabbitMQ paths).
- Contract checks for API documentation and endpoint coverage.
- JaCoCo coverage verification gates.
- PIT mutation testing gate for selected critical services.

## 14. SaaS Compatibility
FIS-Engine is SaaS-compatible as a centrally operated service:
- Multi-tenant API model with tenant isolation.
- Headless architecture consumable by web/mobile/back-office clients.
- Operational controls suitable for managed platform deployment.

Adoption guidance:
- `docs/12-plug-and-play-adoption-guide.md`

## 15. Contribution Standards
- Preserve accounting invariants and append-only behavior.
- Avoid breaking API contracts without explicit versioning/contract updates.
- Keep migrations forward-only and review for audit impact.
- Include tests for all behavior changes, especially financial paths.

## 16. License
Internal project. Usage and distribution follow organizational policy.
