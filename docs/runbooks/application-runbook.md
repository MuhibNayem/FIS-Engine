# FIS Process Application Runbook

## 1. Purpose
This runbook defines standard operating procedures for running, monitoring, troubleshooting, and recovering the `fis-process` service in production.

## 2. Service Summary
- Service: `fis-process`
- Type: Multi-tenant financial ledger engine
- Criticality: Tier-1 financial system
- Data model: Append-only ledger (`fis_journal_entry`, `fis_journal_line`), compensating corrections only

## 3. Ownership and Escalation
- Primary owner: FIS platform engineering
- Secondary owner: SRE/on-call
- Escalation path:
  1. On-call engineer
  2. FIS tech lead
  3. Platform incident commander

## 4. Environments
- `dev`: local developer environment
- `test`: CI/integration tests
- `prod`: production deployment

## 5. Runtime Dependencies
- PostgreSQL 16+
- Redis 7+
- RabbitMQ 3.13+ (Quorum queues)
- OpenTelemetry Collector

## 6. Configuration Baseline
Set through environment variables and secrets.

Required:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_DRIVER=org.postgresql.Driver`
- `REDIS_HOST`
- `REDIS_PORT`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `FIS_SECURITY_ENABLED=true`
- `FIS_JWT_PUBLIC_KEY_PEM` (required; RSA public key in PEM format)
- `FIS_JWT_ENFORCE_TENANT_CLAIM=true`
- `FIS_JWT_TENANT_CLAIM_NAME=tenant_id`
- `FIS_CORS_ALLOWED_ORIGINS=https://your-app.example.com`
- `FIS_OUTBOX_RETENTION_DAYS=30`
- `FIS_OUTBOX_ALERT_RETRY_STREAK_THRESHOLD=5`
- `FIS_OUTBOX_ALERT_OLDEST_UNPUBLISHED_SECONDS=300`
- `FIS_CB_SLIDING_WINDOW_SIZE=20`
- `FIS_CB_MIN_CALLS=10`
- `FIS_CB_FAILURE_RATE_THRESHOLD=50`
- `FIS_CB_WAIT_OPEN_SECONDS=15`
- `FIS_RATE_LIMIT_ENABLED=true` (prod recommended)
- `FIS_RATE_LIMIT_WINDOW_SECONDS=1`
- `FIS_RATE_LIMIT_REQUESTS_PER_WINDOW=60`
- `FIS_RATE_LIMIT_FAIL_OPEN=true`
- `DB_CONNECTION_TIMEOUT_MS=5000`
- `DB_QUERY_TIMEOUT_SECONDS=5`
- `REDIS_CONNECT_TIMEOUT=2s`
- `REDIS_TIMEOUT=2s`
- `RABBITMQ_CONNECTION_TIMEOUT_MS=5000`
- `RABBITMQ_TEMPLATE_REPLY_TIMEOUT_MS=5000`
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector:4318/v1/traces`
- `OTEL_EXPORTER_OTLP_METRICS_ENABLED=true`
- `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=http://otel-collector:4318/v1/metrics`

Recommended production hardening:
- `SERVER_SSL_ENABLED=true`
- `RABBITMQ_SSL_ENABLED=true`
- `REDIS_SSL_ENABLED=true`

## 7. Deployment Procedures

### 7.1 Docker Compose (non-prod/local)
1. Build artifact: `./gradlew clean build`
2. Start stack: `docker compose up --build -d`
3. Verify:
   - `GET /actuator/health`
   - `GET /actuator/health/readiness`
   - RabbitMQ UI reachable

### 7.2 Kubernetes (prod)
Apply manifests in order:
1. `kubectl apply -f deploy/k8s/configmap.yaml`
2. `kubectl apply -f deploy/k8s/secret.yaml`
3. `kubectl apply -f deploy/k8s/deployment.yaml`
4. `kubectl apply -f deploy/k8s/service.yaml`
5. `kubectl apply -f deploy/k8s/hpa.yaml`

Post-deploy validation:
1. Pod status `Running`, readiness `True`
2. Health checks pass
3. Smoke post of one journal entry returns `201`
4. Outbox relay marks events as `published=true`

## 8. Startup Checklist
1. Confirm DB connectivity and migration lock availability.
2. Confirm Redis reachable and key writes functional.
3. Confirm RabbitMQ exchanges/queues present.
4. Confirm JWT auth works for admin and accountant roles.
5. Confirm health endpoints report `UP`.
6. If `FIS_SECURITY_ENABLED=false` (non-prod only), confirm startup log contains `SECURITY_MODE=INSECURE`.
7. If `FIS_SECURITY_ENABLED=false` (non-prod only), confirm metric markers:
   - `fis.security.insecure.mode=1`
   - counter `fis.security.mode.startup{mode="insecure"}`

## 9. Shutdown Checklist
1. Stop ingress traffic (drain/load balancer).
2. Wait until in-flight requests drain.
3. Confirm outbox backlog is near zero.
4. Stop application pods/containers.

## 10. Health, SLOs, and KPIs
- Endpoint health:
  - `/actuator/health`
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
- Target SLOs:
  - Event ingestion throughput: >= 10,000 events/sec
  - REST p99 latency (`POST /v1/journal-entries`): < 200ms
  - Uptime: 99.95%

Track:
- Request rate/error rate/latency by endpoint
- Queue depth (`fis.ingestion.queue`, DLQ)
- Idempotency conflict rates
- DB pool saturation and lock wait times
- Outbox publish metrics:
  - `fis.outbox.publish.success.count`
  - `fis.outbox.publish.failure.count`
  - `fis.outbox.retry.streak`
  - `fis.outbox.oldest.unpublished.age.seconds`
  - `fis.outbox.unpublished.backlog`
- Circuit breaker metrics:
  - `resilience4j.circuitbreaker.calls`
  - `resilience4j.circuitbreaker.state`
- Prometheus scrape endpoint:
  - `/actuator/prometheus`

## 11. Security Operations
- Auth mode: JWT bearer tokens
- Roles:
  - `FIS_ADMIN`: admin/config actions
  - `FIS_ACCOUNTANT`: posting/reversal/correction
  - `FIS_READER`: read-only
- Mandatory tenant header: `X-Tenant-Id`
- Tenant/JWT binding: `X-Tenant-Id` must equal JWT tenant claim (`tenant_id` by default).

Token/secret rotation:
1. Rotate signing key pair in IdP/KMS.
2. Update `FIS_JWT_PUBLIC_KEY_PEM` in secrets manager/config.
3. Roll pods with new public key.
4. Validate auth against newly issued RS256 tokens.

## 12. Routine Operational Tasks

### 12.1 DLQ Inspection and Replay
1. Inspect `fis.ingestion.dlq.queue`.
2. Classify failures:
   - Business-invalid payload/rule/account issues
   - Transient infra failures
3. Correct root cause.
4. Replay corrected messages to `fis.events.exchange`.

### 12.2 Accounting Period Close and Revaluation
1. Ensure rates are uploaded for period end date.
2. Soft-close period.
3. Run revaluation.
4. Hard-close period.

### 12.3 Exchange Rate Upload
1. Upload via `/v1/exchange-rates` as `FIS_ADMIN`.
2. Verify query returns expected values.

### 12.4 Emergency Reopen
1. Reopen latest period first.
2. Reopen earlier period only after subsequent periods are open.
3. Post corrections via append-only reversal/correction endpoints.

## 13. Incident Playbooks

### 13.1 High 5xx Rate
1. Check recent deployment and rollback candidate.
2. Check DB/Rabbit/Redis connectivity.
3. Review logs with trace IDs.
4. If sustained, roll back to last known good release.

### 13.2 Database Connection Exhaustion
1. Verify Postgres health.
2. Inspect Hikari pool metrics.
3. Reduce traffic or scale replicas.
4. Tune pool and query performance before re-opening full traffic.

### 13.3 RabbitMQ Backlog Growth
1. Check consumer health and crash loops.
2. Inspect DLQ and poison-message patterns.
3. Scale consumers and tune prefetch if needed.
4. Confirm ack-after-commit behavior remains intact.
5. If `fis.outbox.retry.streak >= 5` or
   `fis.outbox.oldest.unpublished.age.seconds >= 300`, trigger incident escalation and inspect broker connectivity/publisher errors.

### 13.4 Redis Unavailable
1. Expect idempotency degradation risk.
2. Validate PostgreSQL idempotency fallback behavior.
3. Restore Redis and verify key writes.

### 13.5 Security Incident (token misuse/forgery suspicion)
1. Rotate JWT key pair immediately.
2. Invalidate active sessions/tokens upstream.
3. Review audit logs and access logs by traceId and subject.
4. Open incident report and preserve forensic logs.

## 14. Backup and Restore
- PostgreSQL:
  - Daily full backup
  - PITR/WAL archiving enabled
- Redis:
  - RDB/AOF policy per platform standard
- RabbitMQ:
  - Durable queue persistence enabled

Restore drill:
1. Restore DB snapshot to isolated environment.
2. Run migration validation.
3. Run smoke test and balance integrity checks.
4. Document RTO/RPO achieved.

## 15. Data Integrity Verification
Perform periodic checks:
1. No `UPDATE`/`DELETE` applied to ledger tables.
2. JE balance invariant (`sum(debit)=sum(credit)`) holds.
3. Hash chain continuity checks pass.
4. Reversal uniqueness (`reversal_of_id`) maintained.

## 16. Capacity and Performance Validation
Use scripts under `performance/k6/`:
- `event-ingestion.js` for ingestion throughput
- `journal-posting.js` for API latency

Run before major releases and after infra topology changes.

## 17. Change Management
For any production change:
1. RFC/change ticket approved
2. Migration reviewed for immutability impact
3. Rollout plan and rollback plan prepared
4. Post-deploy verification completed

## 18. Compliance Notes
- Ledger is append-only.
- Corrections are compensating entries only.
- Administrative changes are written to immutable audit log.

## 19. References
- `README.md`
- `docs/SRS.md`
- `docs/05-api-contracts.md`
- `docs/08-implementation-roadmap.md`
- `docs/runbooks/phase6-operations-runbook.md`
