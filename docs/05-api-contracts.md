# API Contracts (REST/JSON)

Authoritative API contract for the current FIS Process service implementation.

Base path: `/v1`  
Media type: `application/json`  
Error format: RFC 7807 `application/problem+json`

## Common Headers

- `Authorization: Bearer <jwt>` (when security enabled)
- `X-Tenant-Id: <uuid>` (required for all business endpoints)
- `traceparent: <w3c-traceparent>` (optional for write/intake endpoints)
- Tenant isolation hardening:
  - When `fis.security.jwt.enforce-tenant-claim=true` (default), `X-Tenant-Id` must match JWT claim `tenant_id` (configurable via `fis.security.jwt.tenant-claim-name`).
  - Mismatch or missing claim returns `403` with problem type `/problems/tenant-context-mismatch`.
- Browser access hardening:
  - CORS is explicitly controlled via `fis.security.cors.allowed-origins`.

## Rate Limiting

- Posting/intake endpoints are rate-limited when `fis.rate-limit.enabled=true`.
- Limiting is Redis-backed and distributed across application instances.
- Guarded paths:
  - `POST /events`
  - `POST /journal-entries`
  - `POST /journal-entries/batch`
  - `POST /revaluations/**`
  - `POST /settlements`
- Exceeded limits return:
  - `429 Too Many Requests`
  - `Content-Type: application/problem+json`
  - `Retry-After: <window-seconds>`
  - Problem type: `/problems/rate-limit-exceeded`
- If Redis is unavailable, limiter behavior is controlled by `fis.rate-limit.fail-open` (default `true`).

## Implemented Endpoints

### Event Intake

- `POST /events`
  - Headers: `X-Tenant-Id`, `X-Source-System`, optional `traceparent`
  - Purpose: Ingest financial event for asynchronous processing.
  - Success: `202 Accepted`
  - Error: `429 Too Many Requests` when posting limiter is active and threshold is exceeded

### Journal Entries

- `POST /journal-entries`
  - Headers: `X-Tenant-Id`, optional `X-Actor-Role`, optional `traceparent`
  - Purpose: Create manual JE (may post immediately or create workflow draft depending on threshold).
  - Body supports optional `effectiveDate` and `transactionDate` (default to `postedDate` when omitted).
  - Success: `201 Created`
  - Error: `429 Too Many Requests` when posting limiter is active and threshold is exceeded

- `POST /journal-entries/batch`
  - Headers: `X-Tenant-Id`, optional `X-Actor-Role`, optional `traceparent`
  - Body: `batchMode` (`POST_NOW` or `CREATE_DRAFTS`) plus `entries[]`.
  - Behavior:
    - `POST_NOW`: all entries must be directly postable; if any requires approval, the full batch is rejected.
    - `CREATE_DRAFTS`: all entries are created as workflow drafts.
  - Idempotency: duplicate `eventId` within the same batch, or against existing JE/workflow rows, rejects the full batch.
  - Success: `201 Created`
  - Error: `429 Too Many Requests` when posting limiter is active and threshold is exceeded

- `GET /journal-entries`
  - Headers: `X-Tenant-Id`
  - Query: `postedDateFrom`, `postedDateTo`, `accountCode`, `status`, `referenceId`, `page`, `size`
  - Success: `200 OK`

- `GET /journal-entries/{id}`
  - Headers: `X-Tenant-Id`
  - Success: `200 OK`

- `POST /journal-entries/{id}/reverse`
  - Headers: `X-Tenant-Id`
  - Purpose: Full reversal JE.
  - Success: `201 Created`

- `POST /journal-entries/{id}/correct`
  - Headers: `X-Tenant-Id`
  - Purpose: Reverse + replacement JE flow.
  - Success: `201 Created`

- `POST /journal-entries/{id}/submit`
  - Headers: `X-Tenant-Id`
  - Body: `submittedBy`
  - Purpose: Move JE workflow from `DRAFT` to `PENDING_APPROVAL`.
  - Success: `200 OK`

- `POST /journal-entries/{id}/approve`
  - Headers: `X-Tenant-Id`, optional `X-Actor-Role`
  - Body: `approvedBy`
  - Purpose: Approve pending JE workflow (maker-checker enforced) and post immutable JE.
  - Success: `201 Created`

- `POST /journal-entries/{id}/reject`
  - Headers: `X-Tenant-Id`
  - Body: `rejectedBy`, `reason`
  - Purpose: Reject pending JE workflow.
  - Success: `200 OK`

### Accounts

- `POST /accounts`
  - Headers: `X-Tenant-Id`, optional `X-Actor-Id`
  - Body supports `contra` accounts.
  - Success: `201 Created`

- `GET /accounts`
  - Headers: `X-Tenant-Id`
  - Query: `accountType`, `isActive`, pageable params
  - Success: `200 OK`

- `GET /accounts/{accountCode}`
  - Headers: `X-Tenant-Id`
  - Success: `200 OK`

- `PATCH /accounts/{accountCode}`
  - Headers: `X-Tenant-Id`, optional `X-Actor-Id`
  - Purpose: Update account name or active state.
  - Success: `200 OK`

- `GET /accounts/{accountCode}/aggregated-balance`
  - Headers: `X-Tenant-Id`
  - Purpose: Return account plus aggregated balance (self + descendants in hierarchy).
  - Success: `200 OK`

### Accounting Periods

- `POST /accounting-periods`
  - Headers: `X-Tenant-Id`
  - Success: `201 Created`

- `GET /accounting-periods`
  - Headers: `X-Tenant-Id`
  - Query: `status`
  - Success: `200 OK`

- `PATCH /accounting-periods/{periodId}/status`
  - Headers: `X-Tenant-Id`, optional `X-Actor-Id`
  - Body: target status
  - Success: `200 OK`

### Exchange Rates

- `POST /exchange-rates`
  - Headers: `X-Tenant-Id`
  - Success: `201 Created`

- `GET /exchange-rates`
  - Headers: `X-Tenant-Id`
  - Query: `sourceCurrency`, `targetCurrency`, optional `effectiveDate`
  - Success: `200 OK`

### Mapping Rules

- `POST /mapping-rules`
  - Headers: `X-Tenant-Id`
  - Success: `201 Created`

- `GET /mapping-rules`
  - Headers: `X-Tenant-Id`
  - Query: `eventType`, `isActive`, `page`, `size`
  - Success: `200 OK`

- `PUT /mapping-rules/{id}`
  - Headers: `X-Tenant-Id`
  - Success: `200 OK`

- `DELETE /mapping-rules/{id}`
  - Headers: `X-Tenant-Id`, optional `X-Actor-Id`
  - Success: `204 No Content`

### FX Revaluation and Settlement

- `POST /revaluations/periods/{periodId}`
  - Headers: `X-Tenant-Id`
  - Purpose: Period-end unrealized FX revaluation.
  - Success: `201 Created`
  - Error: `429 Too Many Requests` when posting limiter is active and threshold is exceeded

- `POST /revaluations/periods/{periodId}/translation`
  - Headers: `X-Tenant-Id`
  - Purpose: Functional-currency translation for income statement items using period average rates; posts CTA to OCI.
  - Body: `eventId`, `createdBy`, `ctaOciAccountCode`, `translationReserveAccountCode`
  - Success: `201 Created`
  - Error: `429 Too Many Requests` when posting limiter is active and threshold is exceeded

- `POST /settlements`
  - Headers: `X-Tenant-Id`
  - Purpose: Realized FX settlement adjustment posting.
  - Success: `201 Created`
  - Error: `429 Too Many Requests` when posting limiter is active and threshold is exceeded

### Admin

- `GET /admin/integrity-check`
  - Headers: `X-Tenant-Id`
  - Purpose: Verify accounting equation totals and delta.
  - Success: `200 OK`

- `POST /admin/year-end-close`
  - Headers: `X-Tenant-Id`
  - Body: `fiscalYear`, `retainedEarningsAccountCode`, `createdBy`
  - Purpose: Execute fiscal year-end close (close P&L balances to retained earnings).
  - Success: `200 OK`

### Reporting

- `GET /reports/trial-balance`
  - Headers: `X-Tenant-Id`
  - Query: `asOfDate` (uses JE `effective_date` for filtering; falls back to `posted_date` for legacy rows)
  - Success: `200 OK`

- `GET /reports/balance-sheet`
  - Headers: `X-Tenant-Id`
  - Query: `asOfDate` (uses JE `effective_date` for filtering; falls back to `posted_date` for legacy rows)
  - Success: `200 OK`

- `GET /reports/income-statement`
  - Headers: `X-Tenant-Id`
  - Query: `fromDate`, `toDate` (uses JE `effective_date` for filtering; falls back to `posted_date` for legacy rows)
  - Success: `200 OK`

- `GET /reports/general-ledger/{accountCode}`
  - Headers: `X-Tenant-Id`
  - Query: `fromDate`, `toDate`
  - Success: `200 OK`

- `GET /reports/cash-flow`
  - Headers: `X-Tenant-Id`
  - Query: `fromDate`, `toDate`
  - Success: `200 OK`

- `GET /reports/account-activity/{accountCode}`
  - Headers: `X-Tenant-Id`
  - Query: `fromDate`, `toDate`
  - Success: `200 OK`

- `GET /reports/journal-register`
  - Headers: `X-Tenant-Id`
  - Query: `fromDate`, `toDate`, `page`, `size`
  - Success: `200 OK`

- `GET /reports/dimension-summary`
  - Headers: `X-Tenant-Id`
  - Query: `dimensionKey`, `fromDate`, `toDate`
  - Success: `200 OK`

- `GET /reports/fx-exposure`
  - Headers: `X-Tenant-Id`
  - Query: `asOfDate`
  - Success: `200 OK`

- `GET /reports/aging`
  - Headers: `X-Tenant-Id`
  - Query: `accountType` (`ASSET`/`LIABILITY`), `asOfDate`
  - Success: `200 OK`

## OpenAPI Source of Truth

Machine-readable spec: `src/main/resources/static/openapi.yaml`  
Swagger UI static page: `src/main/resources/static/swagger-ui.html`
