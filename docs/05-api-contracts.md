# API Contracts (REST/JSON)

Authoritative API contract for the current FIS Process service implementation.

Base path: `/v1`  
Media type: `application/json`  
Error format: RFC 7807 `application/problem+json`

## Common Headers

- `Authorization: Bearer <jwt>` (when security enabled)
- `X-Tenant-Id: <uuid>` (required for all business endpoints)
- `traceparent: <w3c-traceparent>` (optional for write/intake endpoints)

## Implemented Endpoints

### Event Intake

- `POST /events`
  - Headers: `X-Tenant-Id`, `X-Source-System`, optional `traceparent`
  - Purpose: Ingest financial event for asynchronous processing.
  - Success: `202 Accepted`

### Journal Entries

- `POST /journal-entries`
  - Headers: `X-Tenant-Id`, optional `X-Actor-Role`, optional `traceparent`
  - Purpose: Create manual JE (may post immediately or create workflow draft depending on threshold).
  - Success: `201 Created`

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

- `POST /settlements`
  - Headers: `X-Tenant-Id`
  - Purpose: Realized FX settlement adjustment posting.
  - Success: `201 Created`

### Admin

- `GET /admin/integrity-check`
  - Headers: `X-Tenant-Id`
  - Purpose: Verify accounting equation totals and delta.
  - Success: `200 OK`

## OpenAPI Source of Truth

Machine-readable spec: `src/main/resources/static/openapi.yaml`  
Swagger UI static page: `src/main/resources/static/swagger-ui.html`

## Planned (SRS v2.0, Not Yet Implemented Here)

These are required by SRS v2.0 but are not implemented in this service code yet:

- Ledger management APIs (`/ledgers` family)
- Reporting APIs (`/reports/trial-balance`, `/reports/balance-sheet`, `/reports/income-statement`, `/reports/general-ledger/{code}`)
- Batch JE posting (`/journal-entries/batch`)
- Account aggregated balance (`/accounts/{code}/aggregated-balance`)
- Year-end close (`/admin/year-end-close`)

When these are delivered, both OpenAPI and this document must be updated in the same change set.
