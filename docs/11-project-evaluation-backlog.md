# Project Evaluation Backlog (Impact vs Effort)

Date: 2026-03-01  
Scope: Full codebase evaluation (all code files reviewed)

## Prioritization Model

- **P0**: High impact, low-to-medium effort, immediate risk reduction.
- **P1**: High/medium impact, medium effort, important for sustainability.
- **P2**: Medium impact, larger effort or optimization-oriented.

---

## P0 (Do First)

### 1. Unify async ingestion idempotency path with the shared wrapper
- **Impact**: Very high (consistency, correctness under retries, simpler reasoning).
- **Effort**: Medium.
- **Problem**: RabbitMQ consumer still performs manual duplicate checks and direct state marking, while REST path uses unified wrapper.
- **Current files**:
  - `src/main/java/com/bracit/fisprocess/messaging/EventIngestionConsumer.java`
  - `src/main/java/com/bracit/fisprocess/service/impl/IdempotentLedgerWriteServiceImpl.java`
  - `src/main/java/com/bracit/fisprocess/service/impl/FinancialEventIngestionServiceImpl.java`
- **Action**:
  - Refactor consumer flow to delegate ledger writes through `IdempotentLedgerWriteService`.
  - Remove manual duplicate pre-check and duplicate completion writes from consumer.
  - Preserve ack/nack/reject behavior exactly.
- **Acceptance criteria**:
  - Duplicate async messages never create duplicate JEs.
  - Same payload via REST and RabbitMQ results in identical idempotency behavior.
  - Existing ingestion integration tests remain green.

### 2. Harden security-disable configuration pathway
- **Impact**: Very high (misconfiguration blast radius).
- **Effort**: Low.
- **Problem**: `fis.security.enabled=false` still permits all requests outside prod profile.
- **Current files**:
  - `src/main/java/com/bracit/fisprocess/config/SecurityConfig.java`
  - `src/main/java/com/bracit/fisprocess/config/ProductionSecurityGuardConfig.java`
  - `src/main/resources/application.yml`
- **Action**:
  - Restrict security-disabled mode to an explicit `dev/test` profile guard.
  - Add startup warning banner and metric/log marker when security is disabled.
  - Document safe local-dev invocation in runbook.
- **Acceptance criteria**:
  - Security disable cannot be enabled in prod-like profiles.
  - Boot logs clearly indicate security mode.
  - `SecurityRbacIntegrationTest` remains passing.

### 3. Add CI gate for OpenAPI drift
- **Impact**: High (contract reliability).
- **Effort**: Low.
- **Problem**: OpenAPI is manually maintained and large; drift risk grows with endpoint count.
- **Current files**:
  - `src/main/resources/static/openapi.yaml`
  - `docs/05-api-contracts.md`
- **Action**:
  - Add a CI validation task to parse/lint OpenAPI and fail on schema errors.
  - Add a contract check requiring new controller paths to have OpenAPI entries.
- **Acceptance criteria**:
  - Broken OpenAPI fails CI.
  - New endpoint PRs without contract update fail CI.

---

## P1 (Next)

### 4. Split reporting module into focused components
- **Impact**: High (maintainability, reviewability, regression isolation).
- **Effort**: Medium.
- **Problem**: Reporting service/repository are monolithic.
- **Current files**:
  - `src/main/java/com/bracit/fisprocess/service/impl/ReportingServiceImpl.java`
  - `src/main/java/com/bracit/fisprocess/repository/ReportingRepository.java`
  - `src/main/java/com/bracit/fisprocess/controller/FinancialReportingController.java`
- **Action**:
  - Split by report domain (`trial-balance`, `balance-sheet`, `income-statement`, `ledger`, etc.).
  - Extract SQL into smaller repository classes or query objects.
  - Keep external API unchanged.
- **Acceptance criteria**:
  - No class > 300 LOC in reporting module.
  - `FinancialReportingControllerIntegrationTest` stays green.
  - No response contract changes.

### 5. Add deterministic concurrency test for batch + approval edge cases
- **Impact**: High (financial correctness).
- **Effort**: Medium.
- **Problem**: Batch/approval semantics are strict; concurrency and conflict edges need deeper coverage.
- **Current files**:
  - `src/test/java/com/bracit/fisprocess/controller/JournalEntryControllerIntegrationTest.java`
  - `src/test/java/com/bracit/fisprocess/controller/JournalApprovalWorkflowIntegrationTest.java`
  - `src/test/java/com/bracit/fisprocess/controller/ConcurrencyIntegrationTest.java`
- **Action**:
  - Add race tests for same `eventId` across simultaneous batch and single-entry calls.
  - Verify all-or-nothing behavior under contention.
- **Acceptance criteria**:
  - No partial writes in conflict scenarios.
  - Idempotency and approval violations map to stable problem types.

### 6. Strengthen translation/revaluation audit detail payloads
- **Impact**: Medium-high (auditability/compliance).
- **Effort**: Medium.
- **Problem**: Run records capture generated IDs/count, but not full input-rate/exposure trace per currency.
- **Current files**:
  - `src/main/java/com/bracit/fisprocess/service/impl/PeriodEndRevaluationServiceImpl.java`
  - `src/main/java/com/bracit/fisprocess/service/impl/FunctionalCurrencyTranslationServiceImpl.java`
  - `src/main/java/com/bracit/fisprocess/domain/entity/PeriodRevaluationRun.java`
  - `src/main/java/com/bracit/fisprocess/domain/entity/PeriodTranslationRun.java`
- **Action**:
  - Persist structured per-currency computation snapshot in `details` JSONB.
  - Include rates used, signed amounts, carrying base, translated base, and delta.
- **Acceptance criteria**:
  - Audit run payload is reproducible and sufficient for external audit walkthrough.
  - No sensitive data leakage.

---

## P2 (Optimization / Hardening)

### 7. Add explicit coverage thresholds and mutation test pilot
- **Impact**: Medium.
- **Effort**: Medium-high.
- **Problem**: Test suite is comprehensive, but no enforced quantitative quality gate was observed.
- **Current files**:
  - `build.gradle`
  - `src/test/java/**`
- **Action**:
  - Add Jacoco coverage thresholds per package.
  - Add mutation test pilot for critical financial flows.
- **Acceptance criteria**:
  - Build fails if coverage drops below agreed baseline.
  - Mutation score baseline established for core ledger services.

### 8. Improve outbox relay observability and retry analytics
- **Impact**: Medium.
- **Effort**: Medium.
- **Problem**: Relay retries are logged, but limited structured operational metrics.
- **Current files**:
  - `src/main/java/com/bracit/fisprocess/service/impl/OutboxServiceImpl.java`
  - `src/main/java/com/bracit/fisprocess/scheduling/OutboxCleanupJob.java`
- **Action**:
  - Add metrics: publish success/failure counts, retry streak, oldest unpublished age.
  - Add alerting thresholds in ops docs.
- **Acceptance criteria**:
  - Dashboards can identify backpressure and stuck outbox quickly.
  - Runbook includes concrete remediation actions.

### 9. Introduce DB performance guardrails for reporting queries
- **Impact**: Medium.
- **Effort**: Medium.
- **Problem**: Reporting SQL is heavy and likely data-volume sensitive.
- **Current files**:
  - `src/main/java/com/bracit/fisprocess/repository/ReportingRepository.java`
  - `src/main/resources/db/migration/*.sql`
- **Action**:
  - Add explain-plan performance tests for representative large datasets.
  - Add/adjust indexes based on measured query patterns.
- **Acceptance criteria**:
  - p95 report query latency targets defined and met in load tests.
  - Index additions are documented in schema docs.

---

## Recommended Execution Order

1. P0.1 unify async idempotency path  
2. P0.2 security-disable hardening  
3. P0.3 OpenAPI CI drift gate  
4. P1.4 reporting modularization  
5. P1.5 batch/approval concurrency tests  
6. P1.6 translation/revaluation audit detail enrichment  
7. P2.7 coverage + mutation gates  
8. P2.8 outbox observability hardening  
9. P2.9 reporting DB performance guardrails

---

## Definition of Done (Backlog Closure)

- All P0 items merged and verified in CI.
- No regression in full test suite.
- API contract/docs updated for any behavior change.
- Auditability artifacts available for revaluation + translation runs.
- Operational runbook updated for security mode and outbox monitoring.
