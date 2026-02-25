# Low-Level Design: API Contracts (REST/JSON)

All APIs are versioned via URI path. All error responses conform to RFC 7807 `ProblemDetail`.

**Base URL:** `https://api.internal.org/fis/v1`

**Common Request Headers (all endpoints):**
- `Content-Type: application/json`
- `Authorization: Bearer {jwt_token}`
- `X-Tenant-Id: {tenant_uuid}` (REQUIRED)

---

## 1. Financial Event Ingestion

### POST `/events`
Ingest a generic financial event from an upstream domain.

**Additional Headers:**
- `X-Source-System: {string}` (REQUIRED)

**Request Body:**
```json
{
  "eventId": "evt_987654321",
  "eventType": "ORDER_COMPLETED",
  "occurredAt": "2026-02-25T10:00:00Z",
  "transactionCurrency": "GBP",
  "payload": {
    "totalAmountCents": 15000,
    "feeAmountCents": 500,
    "orderId": "ord_555"
  },
  "dimensions": {
    "region": "EU-WEST",
    "channel": "WEB"
  }
}
```

**202 Accepted:**
```json
{
  "status": "ACCEPTED",
  "ik": "evt_987654321",
  "message": "Event queued for ledger processing."
}
```

**409 Conflict** (duplicate `eventId` with different payload):
```json
{
  "type": "/problems/duplicate-idempotency-key",
  "title": "Duplicate Idempotency Key",
  "status": 409,
  "detail": "eventId 'evt_987654321' was already used with a different payload hash.",
  "instance": "/v1/events"
}
```

---

## 2. Manual Journal Entries

### POST `/journal-entries`
Post a manual adjusting entry (Role: `FIS_ADMIN` or `FIS_ACCOUNTANT`).

**Request Body:**
```json
{
  "eventId": "manual_je_2026_02_25_0001",
  "postedDate": "2026-02-25",
  "description": "Manual correction for invoice 9921",
  "referenceId": "INV-9921-CORRECTION",
  "transactionCurrency": "USD",
  "lines": [
    {
      "accountCode": "4000-REVENUE",
      "amountCents": 5000,
      "isCredit": false,
      "dimensions": { "department": "sales" }
    },
    {
      "accountCode": "1000-CASH",
      "amountCents": 5000,
      "isCredit": true,
      "dimensions": { "department": "sales" }
    }
  ]
}
```

**201 Created:**
```json
{
  "journalEntryId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "POSTED",
  "postedAt": "2026-02-25T10:05:00Z"
}
```

**422 Unprocessable Entity:**
```json
{
  "type": "/problems/unbalanced-entry",
  "title": "Unbalanced Journal Entry",
  "status": 422,
  "detail": "Journal Entry is unbalanced. Debits: 5000, Credits: 4000.",
  "instance": "/v1/journal-entries"
}
```

### GET `/journal-entries?postedDateFrom=2026-01-01&postedDateTo=2026-02-28&accountCode=1000-CASH&status=POSTED&page=0&size=50`
Query journal entries with filters.

Supported query params:
- `postedDateFrom` (optional)
- `postedDateTo` (optional)
- `accountCode` (optional)
- `status` (optional)
- `referenceId` (optional)
- `page` (default `0`)
- `size` (default `20`)

**200 OK:**
```json
{
  "content": [
    {
      "journalEntryId": "...",
      "postedDate": "2026-02-25",
      "status": "POSTED",
      "description": "Manual correction",
      "referenceId": "INV-9921-CORRECTION",
      "transactionCurrency": "USD",
      "baseCurrency": "USD",
      "exchangeRate": 1.0,
      "lineCount": 2,
      "createdBy": "admin@company.com",
      "createdAt": "2026-02-25T10:05:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1
}
```

---

## 3. Journal Entry Reversals

### POST `/journal-entries/{journalEntryId}/reverse`
Fully reverse a posted Journal Entry.

**Request Body:**
```json
{
  "eventId": "reverse_550e8400e29b",
  "reason": "Duplicate posting detected for invoice INV-9921"
}
```

**201 Created:**
```json
{
  "reversalJournalEntryId": "...",
  "originalJournalEntryId": "...",
  "status": "POSTED",
  "message": "Reversal entry posted. Original entry remains immutable."
}
```

**409 Conflict** (reversal already exists):
```json
{
  "type": "/problems/invalid-reversal",
  "title": "Invalid Reversal",
  "status": 409,
  "detail": "Journal Entry '550e...' already has a posted reversal entry.",
  "instance": "/v1/journal-entries/550e.../reverse"
}
```

---

## Ledger Immutability Contract

- `fis_journal_entry` and `fis_journal_line` are append-only after posting.
- Corrections must be represented by compensating/reversal entries.
- In PostgreSQL, `UPDATE` and `DELETE` on these ledger tables are blocked by database triggers.

---

## 4. Account Management

### POST `/accounts`
Create a new account in the Chart of Accounts.

```json
{
  "code": "1000-CASH",
  "name": "Cash and Cash Equivalents",
  "accountType": "ASSET",
  "currencyCode": "USD",
  "parentAccountCode": null
}
```

**201 Created:**
```json
{
  "accountId": "...",
  "code": "1000-CASH",
  "name": "Cash and Cash Equivalents",
  "currentBalanceCents": 0
}
```

### GET `/accounts`
List all accounts (paginated, filterable by `accountType`, `isActive`).

### GET `/accounts/{accountCode}`
Get account details including current balance.

**200 OK:**
```json
{
  "accountId": "...",
  "code": "1000-CASH",
  "name": "Cash and Cash Equivalents",
  "accountType": "ASSET",
  "currencyCode": "USD",
  "currentBalanceCents": 12500000,
  "formattedBalance": "125,000.00",
  "isActive": true,
  "parentAccountCode": null,
  "asOf": "2026-02-25T10:06:00Z"
}
```

### PATCH `/accounts/{accountCode}`
Update account name or deactivate. Cannot change `accountType` or `code`.

```json
{
  "name": "Cash & Equivalents",
  "isActive": false
}
```

---

## 5. Accounting Period Management

### POST `/accounting-periods`
Create a new Accounting Period.

```json
{
  "name": "2026-03",
  "startDate": "2026-03-01",
  "endDate": "2026-03-31"
}
```

### GET `/accounting-periods`
List all periods (filterable by `status`).

### PATCH `/accounting-periods/{periodId}/status`
Transition a period's state.

```json
{
  "action": "SOFT_CLOSE"
}
```
Valid actions: `OPEN`, `SOFT_CLOSE`, `HARD_CLOSE`, `REOPEN`.

---

## 6. Mapping Rules Management

### POST `/mapping-rules`
Create a new mapping rule.

```json
{
  "eventType": "SALARY_DISBURSED",
  "description": "Maps payroll salary events to Salary Expense and Bank accounts",
  "lines": [
    { "accountCodeExpression": "6100-SALARY-EXPENSE", "isCredit": false, "amountExpression": "${payload.amountCents}" },
    { "accountCodeExpression": "1000-CASH", "isCredit": true, "amountExpression": "${payload.amountCents}" }
  ]
}
```

### GET `/mapping-rules`
List all rules (filterable by `eventType`, `isActive`).

### PUT `/mapping-rules/{ruleId}`
Update a mapping rule (increments `version`, logged to audit).

### DELETE `/mapping-rules/{ruleId}`
Deactivate a mapping rule (soft delete, sets `isActive = false`).

---

## 7. Exchange Rates

### POST `/exchange-rates`
Upload daily exchange rates.

```json
{
  "rates": [
    { "sourceCurrency": "GBP", "targetCurrency": "USD", "rate": 1.27150000, "effectiveDate": "2026-02-25" },
    { "sourceCurrency": "EUR", "targetCurrency": "USD", "rate": 1.08320000, "effectiveDate": "2026-02-25" }
  ]
}
```

### GET `/exchange-rates?sourceCurrency=GBP&targetCurrency=USD&effectiveDate=2026-02-25`
Query a specific rate.
