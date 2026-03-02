# Reporting Implementation Plan

## Native FIS Reporting Module (Generic & Pluggable Internals)

---

| Field | Value |
| :---- | :---- |
| **Document Title** | Reporting Implementation Plan |
| **Version** | 1.1 |
| **Date** | March 2, 2026 |
| **Prepared By** | Engineering Division |
| **Classification** | Confidential — Internal Use Only |
| **Depends On** | `docs/SRS.md`, `docs/03-architecture.md`, `docs/08-implementation-roadmap.md` |

---

## Implementation Status (2026-03-02)

- Core reporting endpoints are implemented under `/v1/reports/**`.
- Hierarchy-aware line rendering is implemented for Trial Balance, Balance Sheet, and Income Statement with N-level CoA metadata and rolled-up fields.
- OpenAPI reporting schemas are synced with hierarchy-aware response fields.
- Multi-ledger per tenant remains tracked separately in `docs/multi-ledger-implementation-plan.md`.

---

## 1. Purpose

Define a production-grade plan to add built-in financial reporting as a **native module of the FIS service** while keeping internal reporting components **generic, pluggable, and reusable** across different FIS deployments.

This plan avoids hardcoding business-domain assumptions into report logic and introduces internal extension points so each FIS implementation can add project-specific mappings, dimensions, and presentation formats **without creating a separate reporting service**.

### Explicit Boundary
- Reporting is **not** a standalone product or microservice.
- Reporting is implemented **inside the existing FIS process** and deployed with it.
- All reporting APIs are part of the FIS API surface (`/v1/reports/**`).
- “Pluggable” means internal strategy/provider replacement within FIS module boundaries.

---

## 2. Scope

### 2.1 In Scope
- Trial Balance
- General Ledger
- Account Activity
- Income Statement (P&L)
- Balance Sheet
- Cash Flow Statement (indirect method)
- Revaluation impact disclosure in reports
- As-of date and period range reporting
- Multi-currency reporting with base-currency normalization
- Pluggable dimension filters (e.g., cost center, project, region)
- REST APIs + export adapters (JSON/CSV)
- Report auditability and reproducibility

### 2.2 Out of Scope
- Dashboard UI development
- External BI platform implementation
- Data warehouse ETL pipelines

---

## 3. Design Principles (Generic + Pluggable)

1. **Canonical Reporting Model**
   - All reports are generated from canonical ledger facts (`journal_entry`, `journal_line`, `account`, `exchange_rate`, `period`), not from project-specific tables.

2. **Report SPI (Service Provider Interface)**
   - Introduce `ReportProvider` interface:
     - `reportType()`
     - `supports(tenantId, request)`
     - `generate(context, request)`
   - Core module ships default providers.
   - Any project can add custom providers via Spring bean registration without modifying core code.

3. **Chart-of-Accounts Mapping Plugin**
   - Introduce `ReportingClassificationStrategy` SPI for mapping accounts into report sections:
     - assets/liabilities/equity/revenue/expense
     - operating/investing/financing (cash flow)
   - Default strategy uses `AccountType`.
   - Projects can override using custom account metadata/rules.

4. **Dimension Resolver Plugin**
   - Introduce `DimensionResolver` SPI to normalize/filter dimension keys and values across projects.

5. **Stable Contracts**
   - Report request/response DTOs versioned under `/v1/reports`.
   - Additive evolution only; avoid breaking response fields.

6. **Deterministic Output**
   - Same tenant + same request + same ledger state => identical report output.
   - Include report metadata: `generatedAt`, `asOf`, `sourceHash`, `queryVersion`.

---

## 4. Target Architecture (Inside FIS Service)

### 4.1 New Package Structure
- `com.bracit.fisprocess.reporting.api`
- `com.bracit.fisprocess.reporting.service`
- `com.bracit.fisprocess.reporting.provider`
- `com.bracit.fisprocess.reporting.spi`
- `com.bracit.fisprocess.reporting.repository`
- `com.bracit.fisprocess.reporting.dto`

### 4.2 Core Components
- `FinancialReportingController` (`/v1/reports/**`)
- `ReportingService` (dispatch + orchestration)
- `ReportProviderRegistry` (provider discovery + routing)
- Providers:
  - `TrialBalanceReportProvider`
  - `GeneralLedgerReportProvider`
  - `AccountActivityReportProvider`
  - `IncomeStatementReportProvider`
  - `BalanceSheetReportProvider`
  - `CashFlowReportProvider`
- SPI interfaces:
  - `ReportProvider`
  - `ReportingClassificationStrategy`
  - `DimensionResolver`
  - `ReportExportAdapter`

### 4.4 Deployment Model
- No new deployable unit is introduced.
- Reporting code runs in the same Spring Boot application, same security context, and same tenant isolation model.
- Same CI/CD pipeline and operational runbook family apply.

### 4.3 Data Access Pattern
- Read-only, tenant-scoped queries.
- Primary source: normalized ledger tables.
- Optional optimization: materialized views on read replica.
- No mutations in reporting module.

---

## 5. API Plan

Base path: `/v1/reports`

- `GET /trial-balance`
- `GET /general-ledger`
- `GET /account-activity/{accountCode}`
- `GET /income-statement`
- `GET /balance-sheet`
- `GET /cash-flow`

Common request parameters:
- `X-Tenant-Id` (required)
- `fromDate`, `toDate` (or `asOfDate` based on report)
- `baseCurrency` (optional override if supported)
- `dimensions` (optional key/value filters)
- `includeZeroBalances` (optional)
- `format` (`json` default, `csv`)

Common response metadata:
- `reportType`
- `tenantId`
- `fromDate` / `toDate` / `asOfDate`
- `baseCurrency`
- `generatedAt`
- `generatedBy`
- `sourceHash`

---

## 6. Delivery Phases

## Phase 7.1 — Reporting Foundation
- Add reporting packages, DTOs, controller skeleton.
- Implement `ReportProvider` SPI and provider registry.
- Add security rules:
  - `FIS_READER`: read reports
  - `FIS_ACCOUNTANT`: read + export
  - `FIS_ADMIN`: full
- Add OpenAPI contracts for all report endpoints.
- Add error types:
  - `/problems/report-invalid-params`
  - `/problems/report-not-supported`

Acceptance:
- Endpoints accessible with RBAC.
- Provider registry resolves report providers correctly.

## Phase 7.2 — Core Reports (Operational)
- Implement Trial Balance, General Ledger, Account Activity.
- Add pagination for line-heavy reports.
- Add CSV export adapter.
- Add integration tests using Testcontainers PostgreSQL.

Acceptance:
- Trial balance debits == credits.
- General ledger shows deterministic running balances.
- Account activity scoped by account + date range.

## Phase 7.3 — Financial Statements
- Implement Income Statement and Balance Sheet.
- Implement configurable classification via `ReportingClassificationStrategy`.
- Add period comparison support (`current vs prior`).

Acceptance:
- P&L net income ties to equity movement logic.
- Balance sheet balances (Assets == Liabilities + Equity).

## Phase 7.4 — Cash Flow + FX Disclosure
- Implement indirect cash flow.
- Integrate period-end revaluation disclosure section.
- Add FX movement notes for multi-currency tenants.

Acceptance:
- Cash flow reconciliation equals net cash movement for period.
- FX disclosures trace to source JE IDs.

## Phase 7.5 — Performance & Scale Hardening
- Add optional materialized reporting views and refresh strategy.
- Add query plans and index verification for heavy reports.
- Add k6 reporting load tests and performance baselines.

Acceptance:
- p99 report API latency targets met for agreed dataset sizes.
- No cross-tenant leakage under concurrency tests.

---

## 7. Generic Plug-in Contract Details (Internal to FIS)

### 7.1 `ReportProvider`
- Input: `ReportContext`, typed request DTO.
- Output: typed report DTO + metadata.
- Must be pure/read-only and deterministic.

### 7.2 `ReportingClassificationStrategy`
- Methods:
  - classifyForBalanceSheet(account)
  - classifyForIncomeStatement(account)
  - classifyForCashFlow(account, entryContext)
- Allows each FIS implementation to swap classification without touching provider core logic.

### 7.3 `DimensionResolver`
- Normalizes incoming dimension filters.
- Validates supported keys.
- Supports project-specific aliases (e.g., `dept` => `costCenter`).

### 7.4 `ReportExportAdapter`
- Default: JSON, CSV.
- Future-safe for PDF/XLSX plugins.

### 7.5 Non-Standalone Constraint
- Plugins are loaded as FIS Spring beans (classpath/module), not as remote services.
- No network hop between FIS core ledger and reporting module.
- Failure modes remain within one service boundary and one transaction/read consistency model.

---

## 8. Data & Query Strategy

1. Use `base_amount` for normalized multi-currency aggregates.
2. Preserve sign semantics using `is_credit` + account normal balance rules.
3. Restrict to `POSTED`, `REVERSAL`, `CORRECTION` entries according to report type.
4. For as-of reports, include entries with `posted_date <= asOfDate`.
5. Ensure all queries include `tenant_id` predicate first.
6. No reliance on mutable snapshots for authoritative numbers; snapshots are optimization only.

---

## 9. Security, Audit, Compliance

- Enforce JWT + RBAC on all `/v1/reports/**`.
- Validate tenant header with existing tenant filter.
- Log report generation audit event:
  - report type
  - tenant
  - filters
  - requester
  - generation timestamp
- Avoid sensitive payload leakage in logs.
- Maintain immutable evidence by storing `sourceHash` in report metadata.

---

## 10. Testing Strategy

1. **Unit Tests**
   - Provider logic, classification rules, dimension resolution.

2. **Integration Tests**
   - Endpoint correctness with Testcontainers PostgreSQL.
   - Multi-currency statement correctness.
   - Reversal/correction effects on reports.

3. **Property/Invariant Tests**
   - Trial balance totals invariant.
   - Balance sheet equation invariant.

4. **Security Tests**
   - Role access matrix for each endpoint.

5. **Performance Tests**
   - Report generation under large journal volumes.

---

## 11. Documentation Deliverables

- Update `docs/05-api-contracts.md` with `/v1/reports` APIs.
- Update OpenAPI spec with all report schemas.
- Add `docs/runbooks/reporting-runbook.md`:
  - troubleshooting slow reports
  - materialized view refresh procedures
  - reconciliation and correctness checks
- Update `README.md` with reporting module overview and extension guide.

---

## 12. Risks & Mitigations

| Risk | Impact | Mitigation |
| :---- | :---- | :---- |
| Hardcoded accounting classification | Not reusable across projects | SPI-based classification strategy |
| Heavy queries on primary DB | Write path contention | Read replica + materialized views |
| Inconsistent dimension keys across integrations | Incorrect filtering | Dimension resolver plugin + validation |
| Report drift after schema evolution | Breaking consumers | Versioned contracts + compatibility tests |

---

## 13. Definition of Done

- All six report endpoints implemented and documented.
- SPI extension points available and example plugin provided.
- Full RBAC and tenant isolation verified.
- Deterministic report metadata includes `sourceHash`.
- Automated tests green (`unit + integration + security + performance smoke`).
- Runbook and API docs updated.

---

## 14. Recommended Execution Order

1. Build SPI + Trial Balance + General Ledger first.
2. Add Income Statement + Balance Sheet next.
3. Add Cash Flow after classification strategy stabilizes.
4. Add optimization (materialized views/read-replica) only after correctness baseline is complete.
