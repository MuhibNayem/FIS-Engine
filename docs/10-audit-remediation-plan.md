# Audit Remediation Implementation Plan

## Tickable Execution Phases (Production Hardening)

---

| Field | Value |
| :---- | :---- |
| **Document Title** | Audit Remediation Implementation Plan |
| **Version** | 2.0 |
| **Date** | February 26, 2026 |
| **Prepared By** | Engineering Division |
| **Related Audit** | Deep Codebase Audit (8.8/10) |
| **Related Gap Analysis** | [finance-accounting-gap-analysis.md](/home/amnayem/Projects/fis-process/docs/finance-accounting-gap-analysis.md) — 18 gaps identified |
| **Scope** | Full remediation covering correctness, security, compliance, reporting, and operational sustainability |

---

## 1. Goal

Close all audit gaps without breaking business flows, append-only guarantees, or tenant isolation. Phases R1–R8 address the original codebase audit findings. Phases R9–R16 address the 18 finance & accounting gaps.

---

## 2. Phase Overview

### Original Audit Remediation (R1–R8) — Complete

| Phase | Priority | Focus | Gaps Addressed | Status |
| :---- | :---- | :---- | :---- | :---- |
| R1 | P0 | Hash chain concurrency serialization | GAP-04 | [x] |
| R2 | P0 | Redis outage fallback for idempotency | — | [x] |
| R3 | P0 | Production guard for security bypass | GAP-10 (partial) | [x] |
| R4 | P1 | JWT asymmetric key migration (RS256) | — | [x] |
| R5 | P1 | Replace `@Data` on JPA entities safely | — | [x] |
| R6 | P1 | Critical unit test expansion | — | [x] |
| R7 | P1 | SpEL expression parse/cache optimization | — | [x] |
| R8 | P1 | Migration/versioning and doc consistency cleanup | — | [x] |

### Finance & Accounting Gap Remediation (R9–R16) — New

| Phase | Priority | Focus | Gaps Addressed | Status |
| :---- | :---- | :---- | :---- | :---- |
| R9 | P0 | Internal controls & compliance | GAP-08, GAP-09 | [x] |
| R10 | P0 | Ledger integrity hardening | GAP-01, GAP-02, GAP-03 | [x] |
| R11 | P1 | Multi-currency compliance | GAP-05, GAP-06 | [x] |
| R12 | P1 | Financial reporting APIs | GAP-11, GAP-15 | [ ] |
| R13 | P1 | Period management enhancements | GAP-12, GAP-13 | [ ] |
| R14 | P2 | Operational reliability | GAP-17, GAP-18 | [ ] |
| R15 | P2 | Batch operations & multi-date | GAP-16, GAP-14 | [ ] |
| R16 | P3 | Multi-currency translation (future) | GAP-07 | [ ] |
| R5-fix | P1 | Complete `@Data` removal on remaining entities | — | [x] |

---

## 3. Completed Phases (R1–R8)

## R1 — Hash Chain Concurrency Serialization (P0) ✅

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

## R2 — Redis Outage Fallback for Idempotency (P0) ✅

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

## R3 — Production Guard for Security Bypass (P0) ✅

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

## R4 — JWT Asymmetric Migration (P1) ✅

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

## R5 — Replace `@Data` on JPA Entities (P1) ✅

### Implementation Tasks
- [x] Replace entity `@Data` with `@Getter/@Setter` on `JournalLine`, `AccountingPeriod`, `ExchangeRate`, `OutboxEvent`, `MappingRule`, `MappingRuleLine`, `AuditLog`, `PeriodRevaluationRun`.
- [x] Implement safe `equals/hashCode` policy (identifier-based, proxy-safe) on above entities.
- [x] Replace `@Data` on `Account` entity.
- [x] Replace `@Data` on `JournalEntry` entity.
- [x] Prevent lazy association traversal in `toString`.

### Test Tasks
- [x] Add regression tests for entity proxy comparisons and lazy-loading behavior.
- [x] Verify `Account` and `JournalEntry` after `@Data` removal.

### Acceptance Criteria
- [x] No entity class uses `@Data`.
- [x] Hibernate proxy/lazy-loading behavior remains stable.

---

## R6 — Critical Unit Test Expansion (P1) ✅

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

### Acceptance Criteria
- [x] All listed critical services have dedicated unit test classes.
- [x] CI test suite remains stable and green.

---

## R7 — SpEL Parse/Cache Optimization (P1) ✅

### Implementation Tasks
- [x] Introduce expression compilation/cache keyed by rule line expression.
- [x] Keep `SimpleEvaluationContext.forReadOnlyDataBinding()` sandbox.
- [x] Add cache size policy and eviction strategy.

### Acceptance Criteria
- [x] Expression evaluation overhead reduced under load.
- [x] No change in functional mapping behavior.

---

## R8 — Migration & Documentation Consistency Cleanup (P1) ✅

### Implementation Tasks
- [x] Clarify migration numbering notes (`V3/V4` historical renumbering context) in docs.
- [x] Align all roadmap/docs references with current migration file names.
- [x] Add changelog entry summarizing audit remediations.

### Acceptance Criteria
- [x] No contradictory phase/migration statements across docs.
- [x] Audit remediations are traceable from docs.

---

## 4. New Phases — Finance & Accounting Gap Remediation (R9–R16)

## R9 — Internal Controls & Compliance (P0)

> Addresses: **GAP-08** (JE Approval Workflow) · **GAP-09** (Sequential JE Numbering)

### R9.1 — Journal Entry Approval Workflow (GAP-08)

#### Implementation Tasks
- [x] Add workflow-backed draft and approval flow while keeping `fis_journal_entry` immutable append-only.
- [x] Modify `JournalEntryServiceImpl.createJournalEntry()` to save as `DRAFT` when above configurable threshold.
- [x] Ensure DRAFT entries do NOT update account balances or hash chain.
- [x] Add `POST /v1/journal-entries/{id}/submit` endpoint (DRAFT → PENDING_APPROVAL).
- [x] Add `POST /v1/journal-entries/{id}/approve` endpoint (PENDING_APPROVAL → POSTED, triggers balance/hash/outbox).
- [x] Add `POST /v1/journal-entries/{id}/reject` endpoint (PENDING_APPROVAL → REJECTED).
- [x] Enforce maker-checker: `approvedBy ≠ createdBy`.
- [x] Add configurable approval threshold (`fis.approval.threshold-cents`).
- [x] Update `SecurityConfig` RBAC rules for new endpoints.

#### Test Tasks
- [x] Unit test: JE above threshold → saved as DRAFT, balances unchanged.
- [x] Unit test: JE below threshold → auto-posted (backward compatible).
- [x] Unit test: Maker-checker violation → `ApprovalViolationException`.
- [x] Integration test: Full DRAFT → SUBMIT → APPROVE → POSTED flow.
- [x] Integration test: DRAFT → SUBMIT → REJECT flow.

#### Acceptance Criteria
- [x] No JE above threshold is auto-posted.
- [x] DRAFT entries have zero impact on account balances and hash chain.
- [x] Self-approval is impossible (`approvedBy ≠ createdBy`).

---

### R9.2 — Sequential Journal Entry Numbering (GAP-09)

#### Implementation Tasks
- [x] Add `sequence_number` and `fiscal_year` columns to `fis_journal_entry`.
- [x] Add Flyway migration with `ALTER TABLE` and backfill existing rows.
- [x] Implement tenant/year-scoped sequence allocator with `SELECT ... FOR UPDATE`.
- [x] Add `sequenceNumber` to `JournalEntryResponseDto`.
- [x] Add unique index: `UNIQUE (tenant_id, fiscal_year, sequence_number)`.

#### Test Tasks
- [x] Integration test: Posted entries contain populated sequence number and fiscal year.

#### Acceptance Criteria
- [x] Every JE has a sequential number per tenant/year.
- [x] UUID remains as primary key; sequence number is the audit reference.

---

## R10 — Ledger Integrity Hardening (P0)

> Addresses: **GAP-01** (Min 2-Line) · **GAP-02** (Contra Accounts) · **GAP-03** (Equation Integrity)

### R10.1 — Minimum Two-Line Enforcement (GAP-01)

#### Implementation Tasks
- [x] Add explicit check in `JournalEntryValidationServiceImpl`: require ≥1 debit AND ≥1 credit line.
- [x] Add clear error message in `UnbalancedEntryException`.

#### Test Tasks
- [x] Unit test: Single debit-only entry → rejected.
- [x] Unit test: Two lines (1 debit + 1 credit) → accepted.

#### Acceptance Criteria
- [x] No single-sided JE can be posted, even via SpEL mapping rules.

---

### R10.2 — Contra Account Support (GAP-02)

#### Implementation Tasks
- [x] Add `is_contra BOOLEAN NOT NULL DEFAULT FALSE` to `fis_account` (Flyway migration).
- [x] Add contra flag to `Account` entity, `CreateAccountRequestDto`, and `AccountResponseDto`.
- [x] Update `LedgerPersistenceServiceImpl.computeBalanceDelta()` to invert sign when contra is true.
- [x] Update `AccountServiceImpl` audit and mapping paths for the new field.

#### Test Tasks
- [x] Integration test: Full JE posting with contra account verifies correct balance.

#### Acceptance Criteria
- [x] Contra accounts have reversed balance polarity.
- [x] Existing non-contra accounts unaffected (backward compatible).

---

### R10.3 — Accounting Equation Integrity Check (GAP-03)

#### Implementation Tasks
- [x] Add `GET /v1/admin/integrity-check` endpoint.
- [x] Implement query: `SELECT account_type, SUM(current_balance) FROM fis_account WHERE tenant_id = ? GROUP BY account_type`.
- [x] Verify: `ΣAsset − ΣLiability − ΣEquity − ΣRevenue + ΣExpense == 0`.
- [x] Add `@Scheduled` task running hourly with alert logging.
- [x] Return structured response with per-type totals and pass/fail.

#### Test Tasks
- [x] Unit test: Balanced ledger → OK response.
- [x] Unit test: Corrupted balance (simulated) → FAILED response with details.
- [x] Integration test: `/v1/admin/integrity-check` returns equation status and totals.

#### Acceptance Criteria
- [x] Equation violations are detected and logged automatically.
- [x] Admin API provides on-demand verification.

---

## R11 — Multi-Currency Compliance (P1)

> Addresses: **GAP-05** (Realized FX) · **GAP-06** (Rounding)

### R11.1 — Realized FX Gains/Losses (GAP-05)

#### Implementation Tasks
- [x] Add `SettlementService` interface and implementation.
- [x] Accept settlement event: original JE reference, settlement rate, settlement date.
- [x] Compute `realized gain/loss = (settlement rate − booking rate) × settled amount`.
- [x] Generate a realized gain/loss JE using designated gain/loss accounts.
- [x] Add `POST /v1/settlements` endpoint or a mapping rule event type.

#### Test Tasks
- [x] Unit test: Settlement at higher rate → realized gain JE.
- [x] Unit test: Settlement at lower rate → realized loss JE.
- [x] Unit test: Same rate → no gain/loss JE generated.

#### Acceptance Criteria
- [x] Realized FX gains/losses are recognized per IAS 21 para. 28.

---

### R11.2 — Rounding Difference Handling (GAP-06)

#### Implementation Tasks
- [x] Implement "largest remainder" allocation in `MultiCurrencyServiceImpl.apply()`.
- [x] After converting all lines, compute residual: `round(total) − sum(rounded_lines)`.
- [x] Allocate residual to the line with the largest fractional remainder.
- [x] Add logging for applied rounding adjustments.

#### Test Tasks
- [x] Unit test: 3+ lines where individual rounding creates ±1 cent discrepancy → corrected.
- [x] Unit test: Same-currency (no conversion) → no rounding applied.

#### Acceptance Criteria
- [x] Base-currency JE lines always sum to the correctly rounded total.
- [x] No systematic base-currency drift over time.

---

## R12 — Financial Reporting APIs (P1)

> Addresses: **GAP-11** (Reports) · **GAP-15** (Hierarchy Aggregation)

### R12.1 — Core Financial Reports (GAP-11)

#### Implementation Tasks
- [ ] Add `ReportingService` interface and implementation.
- [ ] Implement `GET /v1/reports/trial-balance?tenantId=&asOfDate=`.
- [ ] Implement `GET /v1/reports/balance-sheet?tenantId=&asOfDate=`.
- [ ] Implement `GET /v1/reports/income-statement?tenantId=&fromDate=&toDate=`.
- [ ] Implement `GET /v1/reports/general-ledger/{accountCode}?fromDate=&toDate=` (transactions with running balance).
- [ ] Add response DTOs for each report type.
- [ ] Secure under `FIS_READER` role (read-only).

#### Test Tasks
- [ ] Integration test: Trial Balance after posting sample JEs → debits == credits.
- [ ] Integration test: Balance Sheet → Assets == Liabilities + Equity.
- [ ] Integration test: General Ledger → running balance matches account's current balance.

#### Acceptance Criteria
- [ ] All four report types return correct, tenant-scoped data.
- [ ] Reports are read-only and do not modify any state.

---

### R12.2 — Chart of Accounts Hierarchy Aggregation (GAP-15)

#### Implementation Tasks
- [ ] Add recursive CTE query to `AccountRepository`:
  ```sql
  WITH RECURSIVE account_tree AS (
      SELECT account_id, parent_account_id, current_balance
      FROM fis_account WHERE tenant_id = ? AND code = ?
      UNION ALL
      SELECT a.account_id, a.parent_account_id, a.current_balance
      FROM fis_account a
      JOIN account_tree t ON a.parent_account_id = t.account_id
  )
  SELECT SUM(current_balance) FROM account_tree
  ```
- [ ] Add `GET /v1/accounts/{code}/aggregated-balance` endpoint.
- [ ] Add `aggregatedBalance` to account response (optional field).

#### Test Tasks
- [ ] Integration test: Parent with 3 children → aggregated balance = sum of children.
- [ ] Integration test: Account with no children → aggregated = own balance.

#### Acceptance Criteria
- [ ] Parent account queries return sum of all descendant balances.

---

## R13 — Period Management Enhancements (P1)

> Addresses: **GAP-12** (Year-End Close) · **GAP-13** (Auto-Reversing)

### R13.1 — Fiscal Year-End Close (GAP-12)

#### Implementation Tasks
- [ ] Add `POST /v1/admin/year-end-close` endpoint.
- [ ] Compute net income: `Σ Revenue balances − Σ Expense balances` for the fiscal year.
- [ ] Generate closing JE: Dr. all Revenue accounts, Cr. all Expense accounts, net to Retained Earnings.
- [ ] Zero out Revenue/Expense `current_balance` values.
- [ ] Require `FIS_ADMIN` role and `HARD_CLOSED` status for all periods in the fiscal year.

#### Test Tasks
- [ ] Integration test: Year-end close → Revenue/Expense balances = 0, Retained Earnings = net income.
- [ ] Unit test: Attempt close with OPEN periods → rejected.

#### Acceptance Criteria
- [ ] Year-end closing entry is correctly generated and posted.
- [ ] Revenue/Expense balances are zeroed for the new fiscal year.

---

### R13.2 — Auto-Reversing Accrual Entries (GAP-13)

#### Implementation Tasks
- [ ] Add `auto_reverse BOOLEAN NOT NULL DEFAULT FALSE` to `fis_journal_entry` (Flyway migration).
- [ ] Add `autoReverse` to `CreateJournalEntryRequestDto` and `JournalEntry` entity.
- [ ] Add `@Scheduled` job that triggers on period open: query all `auto_reverse = true` JEs from the prior period and generate reversal entries dated to the first day of the new period.

#### Test Tasks
- [ ] Unit test: Auto-reverse flagged JE → reversal generated on period open.
- [ ] Unit test: Non-flagged JE → no auto-reversal.

#### Acceptance Criteria
- [ ] Accrual reversals are generated automatically without manual intervention.

---

## R14 — Operational Reliability (P2)

> Addresses: **GAP-17** (Consumer Idempotency) · **GAP-18** (Outbox Cleanup)

### R14.1 — Unify Consumer Idempotency Path (GAP-17)

#### Implementation Tasks
- [ ] Refactor `EventIngestionConsumer` to use `IdempotentLedgerWriteServiceImpl.execute()` instead of manual idempotency checks.
- [ ] Remove duplicate `existsByTenantIdAndEventId` / `markCompleted` / `markFailed` calls from consumer.
- [ ] Ensure RabbitMQ ack/nack/reject behavior is preserved post-refactor.

#### Test Tasks
- [ ] Integration test: Duplicate RabbitMQ message → idempotent (no double posting).
- [ ] Integration test: Same event via REST and RabbitMQ → identical idempotency behavior.

#### Acceptance Criteria
- [ ] Single idempotency code path for both REST and RabbitMQ intake channels.

---

### R14.2 — Outbox Cleanup Job (GAP-18)

#### Implementation Tasks
- [ ] Add `@Scheduled` job to purge `fis_outbox` entries where `published = true AND created_at < NOW() - retention_period`.
- [ ] Add configurable retention period: `fis.outbox.retention-days` (default: 30).
- [ ] Add logging for purge counts.

#### Test Tasks
- [ ] Unit test: Published entries older than retention → deleted.
- [ ] Unit test: Unpublished entries → never deleted regardless of age.

#### Acceptance Criteria
- [ ] Outbox table does not grow unboundedly.
- [ ] Unpublished entries are never deleted.

---

## R15 — Batch Operations & Multi-Date (P2)

> Addresses: **GAP-16** (Batch Posting) · **GAP-14** (Effective Date)

### R15.1 — Batch Journal Entry Posting (GAP-16)

#### Implementation Tasks
- [ ] Add `POST /v1/journal-entries/batch` accepting a list of JE requests.
- [ ] Validate all JEs before persisting any (fail-fast).
- [ ] Persist all within a single `@Transactional` boundary.
- [ ] Return list of created JE IDs or batch failure details.

#### Test Tasks
- [ ] Integration test: 10 valid JEs → all posted atomically.
- [ ] Integration test: 9 valid + 1 invalid → entire batch rejected, no balances changed.

#### Acceptance Criteria
- [ ] Batch is atomic: all-or-nothing.

---

### R15.2 — Effective Date Distinction (GAP-14)

#### Implementation Tasks
- [ ] Add `effective_date DATE` and `transaction_date DATE` to `fis_journal_entry` (Flyway migration).
- [ ] Default both to `posted_date` when not provided.
- [ ] Add to DTOs and expose in API responses.
- [ ] Use `effective_date` in reporting queries when present.

#### Test Tasks
- [ ] Unit test: JE without effective_date → defaults to posted_date.
- [ ] Unit test: JE with effective_date → stored and returned correctly.

#### Acceptance Criteria
- [ ] Backward compatible — existing JEs unaffected.

---

## R16 — Multi-Currency Translation (P3 — Future)

> Addresses: **GAP-07** (Functional Currency Translation / CTA)

### Implementation Tasks
- [ ] Design CTA/OCI translation model for revenue/expense accounts.
- [ ] Implement average-rate translation for income statement items.
- [ ] Post CTA differences to an OCI equity account.

### Acceptance Criteria
- [ ] Scope and design approved before implementation begins.

---

## R5-fix — Complete `@Data` Removal (P1) ✅

> Addresses: Incomplete R5 — `Account` and `JournalEntry` still use `@Data`

### Implementation Tasks
- [x] Replace `@Data` with `@Getter/@Setter` on `Account.java`.
- [x] Replace `@Data` with `@Getter/@Setter` on `JournalEntry.java`.
- [x] Add `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with ID-only equality on both.
- [x] Verify no serialization or mapping regressions.

### Test Tasks
- [x] Regression test: Account persistence and retrieval unchanged.
- [x] Regression test: JournalEntry with lines — lazy loading still works.

### Acceptance Criteria
- [x] Zero JPA entity classes use `@Data`.

---

## 5. Execution Order

### Already Complete
1. R1 → R2 → R3 (all P0) ✅
2. R4 + R5 in parallel ✅ (R5 partially)
3. R6 then R7 ✅
4. R8 documentation ✅

### New Execution Order
1. **R5-fix** — Complete `@Data` removal (quick win, ~30 min)
2. **R9** — Internal controls (approval workflow + sequential numbering) — P0, non-negotiable for compliance
3. **R10** — Ledger integrity (min 2-line, contra accounts, equation check) — P0, correctness
4. **R11** — Multi-currency (realized FX, rounding) — P1, compliance
5. **R12** + **R13** in parallel — Reporting + Period management — P1
6. **R14** — Operational reliability — P2
7. **R15** — Batch + multi-date — P2
8. **R16** — Future scope — P3

---

## 6. Exit Criteria

### Previous Phase (R1–R8)
- [x] All P0 phases complete and verified in CI.
- [x] All P1 phases complete and verified in CI.
- [x] Full regression test run passes.
- [x] No append-only or idempotency regression introduced.
- [ ] Security review sign-off completed.

### New Phase (R9–R16)
- [x] R5-fix: Zero `@Data` on JPA entities.
- [x] R9: Approval workflow operational with maker-checker enforcement.
- [x] R9: Sequential numbering deployed and backfilled.
- [x] R10: Contra accounts, equation check, and min 2-line all enforced.
- [x] R11: FX rounding corrected; realized gains/losses operational.
- [ ] R12: Trial Balance, Balance Sheet, Income Statement, and GL Detail APIs deployed.
- [ ] R13: Year-end close and auto-reversing entries operational.
- [ ] R14: Single idempotency path; outbox cleanup running.
- [ ] R15: Batch posting available; effective dates supported.
- [ ] Full regression test run passes after all phases.
- [ ] Gap analysis document updated to reflect all closures.

---

## 7. Gap-to-Phase Traceability Matrix

| GAP ID | Description | Phase | Status |
|---|---|---|---|
| GAP-01 | Min 2-line enforcement | R10.1 | [x] |
| GAP-02 | Contra account support | R10.2 | [x] |
| GAP-03 | Equation integrity check | R10.3 | [x] |
| GAP-04 | Hash chain race condition | R1 | [x] |
| GAP-05 | Realized FX gains/losses | R11.1 | [x] |
| GAP-06 | Rounding difference handling | R11.2 | [x] |
| GAP-07 | Functional currency translation | R16 | [ ] |
| GAP-08 | JE approval workflow | R9.1 | [x] |
| GAP-09 | Sequential JE numbering | R9.2 | [x] |
| GAP-10 | Security bypass guard | R3 | [x] |
| GAP-11 | Financial reporting APIs | R12.1 | [ ] |
| GAP-12 | Year-end close process | R13.1 | [ ] |
| GAP-13 | Auto-reversing entries | R13.2 | [ ] |
| GAP-14 | Effective date distinction | R15.2 | [ ] |
| GAP-15 | CoA hierarchy aggregation | R12.2 | [ ] |
| GAP-16 | Batch JE posting | R15.1 | [ ] |
| GAP-17 | Consumer idempotency bypass | R14.1 | [ ] |
| GAP-18 | Outbox cleanup/archival | R14.2 | [ ] |
