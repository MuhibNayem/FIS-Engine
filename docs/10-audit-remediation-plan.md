# Audit Remediation Implementation Plan

## Tickable Execution Phases (Production Hardening)

---

| Field | Value |
| :---- | :---- |
| **Document Title** | Audit Remediation Implementation Plan |
| **Version** | 1.0 |
| **Date** | February 26, 2026 |
| **Prepared By** | Engineering Division |
| **Related Audit** | Deep Codebase Audit (8.8/10) |
| **Scope** | P0/P1 remediation for correctness, security, reliability, and test depth |

---

## 1. Goal

Close all high-priority and medium-priority audit gaps without breaking business flows, append-only guarantees, or tenant isolation.

---

## 2. Phase Overview

| Phase | Priority | Focus | Status |
| :---- | :---- | :---- | :---- |
| R1 | P0 | Hash chain concurrency serialization | [x] |
| R2 | P0 | Redis outage fallback for idempotency | [x] |
| R3 | P0 | Production guard for security bypass | [x] |
| R4 | P1 | JWT asymmetric key migration (RS256) | [x] |
| R5 | P1 | Replace `@Data` on JPA entities safely | [x] |
| R6 | P1 | Critical unit test expansion | [x] |
| R7 | P1 | SpEL expression parse/cache optimization | [x] |
| R8 | P1 | Migration/versioning and doc consistency cleanup | [x] |

---

## 3. Detailed Tickable Plan

## R1 — Hash Chain Concurrency Serialization (P0)

### Implementation Tasks
- [x] Add tenant-level hash sequencing strategy (DB-backed lock row or equivalent).
- [x] Ensure latest-hash read + new-hash write occur under the same lock scope.
- [x] Update `LedgerPersistenceService`/hash flow to use serialized tenant hash update path.
- [x] Add migration for hash-lock state table if required.
- [x] Add metrics/logging for lock wait and contention visibility.

### Test Tasks
- [x] Add concurrent posting test for same tenant proving no chain forks.
- [x] Add invariant test validating strict previous-hash continuity across inserted entries.

### Acceptance Criteria
- [x] No duplicate previous-hash usage under high concurrency.
- [x] Hash chain remains linear and verifiable for each tenant.

---

## R2 — Redis Outage Fallback for Idempotency (P0)

### Implementation Tasks
- [x] Implement failover path: if Redis check fails, use PostgreSQL idempotency check-and-mark atomically.
- [x] Add bounded retry/backoff for Redis transient errors.
- [x] Prevent infinite requeue loops on persistent Redis outage.
- [x] Add clear operational mode marker in logs (`redis-primary`, `postgres-fallback`).

### Test Tasks
- [x] Add unit tests for Redis unavailable scenario.
- [x] Add integration test simulating Redis disconnect during intake.
- [x] Add duplicate-event behavior test in fallback mode (same payload / different payload).

### Acceptance Criteria
- [x] Exactly-once guarantees preserved when Redis is down.
- [x] System degrades gracefully without business deadlock/requeue storm.

---

## R3 — Production Guard for Security Bypass (P0)

### Implementation Tasks
- [x] Block `fis.security.enabled=false` in `prod` profile (fail-fast startup).
- [x] Add explicit config validation bean for security-critical settings.
- [x] Update deployment config to ensure security cannot be disabled in production.

### Test Tasks
- [x] Add config-level test: prod + security disabled => startup failure.
- [x] Add regression test: non-prod can still disable security for local/dev.

### Acceptance Criteria
- [x] Impossible to run production profile with security disabled.

---

## R4 — JWT Asymmetric Migration (P1)

### Implementation Tasks
- [x] Add RS256 verification support via public key/JWK.
- [x] Provide migration-safe compatibility path and complete cutover.
- [x] Remove HMAC mode after migration cutover window.
- [x] Update runbook for key rotation and incident handling.

### Test Tasks
- [x] Add RBAC tests with asymmetric tokens.
- [x] Add negative tests for invalid signature, wrong key, expired token.

### Acceptance Criteria
- [x] JWT verification uses asymmetric keys in production path.
- [x] Key rotation procedure documented and tested.

---

## R5 — Replace `@Data` on JPA Entities (P1)

### Implementation Tasks
- [x] Replace entity `@Data` with `@Getter/@Setter`.
- [x] Implement safe `equals/hashCode` policy (identifier-based, proxy-safe).
- [x] Prevent lazy association traversal in `toString`.
- [x] Apply consistently across all JPA entities.

### Test Tasks
- [x] Add regression tests for entity proxy comparisons and lazy-loading behavior.
- [x] Verify no serialization or mapping regressions after replacement.

### Acceptance Criteria
- [x] No entity class uses `@Data`.
- [x] Hibernate proxy/lazy-loading behavior remains stable.

---

## R6 — Critical Unit Test Expansion (P1)

### Implementation Tasks
- [x] Add unit tests for `MultiCurrencyServiceImpl`.
- [x] Add unit tests for `AccountingPeriodServiceImpl`.
- [x] Add unit tests for `RuleMappingServiceImpl`.
- [x] Add unit tests for `PeriodEndRevaluationServiceImpl`.
- [x] Add unit tests for `JournalReversalServiceImpl`.
- [x] Add unit tests for `ExchangeRateServiceImpl`.
- [x] Add unit tests for `MappingRuleServiceImpl`.
- [x] Add unit tests for `RedisIdempotencyServiceImpl`.
- [x] Add unit tests for `PeriodValidationServiceImpl`.

### Test Quality Gates
- [x] Cover happy path + key failure path for each service.
- [x] Cover edge-case math/rounding and state-transition invariants.
- [x] Cover duplicate/replay and payload mismatch cases.

### Acceptance Criteria
- [x] All listed critical services have dedicated unit test classes.
- [x] CI test suite remains stable and green.

---

## R7 — SpEL Parse/Cache Optimization (P1)

### Implementation Tasks
- [x] Introduce expression compilation/cache keyed by rule line expression.
- [x] Keep `SimpleEvaluationContext.forReadOnlyDataBinding()` sandbox.
- [x] Add cache size policy and eviction strategy.

### Test Tasks
- [x] Add correctness tests for cached expression behavior.
- [x] Add micro-benchmark/perf comparison test (baseline vs cached).

### Acceptance Criteria
- [x] Expression evaluation overhead reduced under load.
- [x] No change in functional mapping behavior.

---

## R8 — Migration & Documentation Consistency Cleanup (P1)

### Implementation Tasks
- [x] Clarify migration numbering notes (`V3/V4` historical renumbering context) in docs.
- [x] Align all roadmap/docs references with current migration file names.
- [x] Add changelog entry summarizing audit remediations.

### Acceptance Criteria
- [x] No contradictory phase/migration statements across docs.
- [x] Audit remediations are traceable from docs.

---

## 4. Execution Order

1. R1 → R2 → R3 (all P0).
2. R4 + R5 in parallel where possible.
3. R6 then R7.
4. R8 final documentation closure.

---

## 5. Exit Criteria (Phase Complete)

- [x] All P0 phases complete and verified in CI.
- [x] All P1 phases complete and verified in CI.
- [x] Full regression test run passes.
- [x] No append-only or idempotency regression introduced.
- [ ] Security review sign-off completed.
