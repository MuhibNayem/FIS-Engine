# Plug-and-Play Adoption Guide

## 1. Purpose
This guide defines how to adopt FIS-Engine as a headless backend with minimal integration friction, while keeping accounting controls and security intact.

## 2. What "Plug-and-Play" Means Here
- You can run FIS-Engine as a standalone service and connect any frontend or upstream system through REST APIs.
- You do not need to embed code into the host project.
- Integration succeeds only if required contracts are respected:
  - Auth: JWT
  - Tenant scoping: `X-Tenant-Id`
  - Idempotency/event discipline: unique `eventId` semantics
  - Accounting invariants: balanced debit/credit entries

## 3. 15-Minute Quickstart (Local)
1. Start dependencies and service:
```bash
docker compose up --build -d
```
2. Verify health:
```bash
curl -s http://localhost:8080/actuator/health
```
3. Open API contract:
- `http://localhost:8080/openapi.yaml`
- `http://localhost:8080/swagger-ui.html`
4. Call a read endpoint with tenant header:
```bash
curl -s -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Authorization: Bearer <jwt>" \
  http://localhost:8080/v1/accounts
```

## 4. Integration Modes

### A) API Service Mode (recommended)
- Keep FIS-Engine as its own deployable service.
- Host systems integrate through REST and async events.
- Best for most projects and teams.

### B) Platform Mode (multi-team shared backend)
- Same as API service mode, but with shared SRE/observability ownership.
- Requires stricter tenant governance and release process.

## 5. Required Runtime Dependencies
- PostgreSQL 16+
- Redis 7+
- RabbitMQ 3.13+ (Quorum queues recommended)
- OpenTelemetry collector (recommended in production)

See full operational baseline in:
- `docs/runbooks/application-runbook.md`

## 6. Required Security and Tenant Contracts
- `FIS_SECURITY_ENABLED=true` in production.
- `X-Tenant-Id` is required on business endpoints.
- JWT tenant claim binding is enabled by default:
  - `FIS_JWT_ENFORCE_TENANT_CLAIM=true`
  - `FIS_JWT_TENANT_CLAIM_NAME=tenant_id`
- CORS allow-list must be explicit:
  - `FIS_CORS_ALLOWED_ORIGINS=https://your-app.example.com`

## 7. Minimum Config Set for New Integrators
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- `FIS_SECURITY_ENABLED=true`
- `FIS_JWT_PUBLIC_KEY_PEM=<RSA public key>`
- `FIS_JWT_ENFORCE_TENANT_CLAIM=true`
- `FIS_JWT_TENANT_CLAIM_NAME=tenant_id`
- `FIS_RATE_LIMIT_ENABLED=true` (production recommended)

## 8. API-Onboarding Checklist
- Consume the OpenAPI contract from `src/main/resources/static/openapi.yaml`.
- Implement common headers in client middleware:
  - `Authorization`
  - `X-Tenant-Id`
  - optional `traceparent`
- Implement client retry strategy for `429` and transient `5xx`.
- Ensure write calls send stable event identifiers and are idempotent by design.
- Handle RFC7807 `application/problem+json` responses centrally.

## 9. Financial Controls You Get Out-of-the-Box
- Double-entry validation (debits == credits).
- Append-only immutable posted ledger rows.
- Approval workflow (submit/approve/reject) with maker-checker enforcement.
- Sequential numbering by tenant/fiscal year for posted journal entries.
- Reporting APIs:
  - Trial Balance
  - Balance Sheet
  - Income Statement
  - Additional ledger/risk reports

See:
- `docs/05-api-contracts.md`
- `docs/04-database-schema.md`

## 10. Definition of Done for "Plug-and-Play Integrated"
A consuming project is considered integrated when all are true:
1. Health/readiness checks pass in target environment.
2. JWT auth and tenant claim/header binding validated end-to-end.
3. At least one successful post + approval flow executed.
4. At least one reporting endpoint consumed successfully.
5. Monitoring captures:
   - HTTP latency/error rate
   - outbox lag metrics
   - circuit breaker metrics
   - security mode marker metrics
6. Runbook handoff completed with the owning team.

## 11. Common Failure Modes and Fixes
- `400` missing/invalid tenant header:
  - Ensure `X-Tenant-Id` is present and valid UUID.
- `403` tenant context mismatch:
  - Ensure JWT claim (default `tenant_id`) equals `X-Tenant-Id`.
- `429` rate limited:
  - Respect `Retry-After` and use bounded backoff.
- Posting rejected as unbalanced:
  - Validate debit/credit totals client-side before submit.

## 12. Next Optional Improvements
- Publish generated SDKs (Java/TypeScript) from OpenAPI in CI.
- Add partner-facing Postman collection with environment templates.
- Add Helm chart and release notes for one-command production install.
