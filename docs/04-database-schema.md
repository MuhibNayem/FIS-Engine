# Low-Level Design: Database Schema (DDL)

This document outlines the complete PostgreSQL schema for the Generic FIS Engine. The design prioritizes immutability, high-speed appends, multi-tenancy, multi-currency, and strict constraint enforcement. All tables are prefixed with `fis_` for namespace isolation.

**Migration Strategy:** Each section below corresponds to one or more Flyway migration files in `src/main/resources/db/migration/`.

---

## 1. Business Entity / Tenant (`V1__create_business_entity.sql`)

Represents an isolated organizational unit. All subsequent tables reference this via `tenant_id`.

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE fis_business_entity (
    tenant_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255),
    base_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

---

## 2. Chart of Accounts (`V2__create_accounts.sql`)

```sql
CREATE TABLE fis_account (
    account_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency_code VARCHAR(3) NOT NULL,
    current_balance BIGINT NOT NULL DEFAULT 0,
    parent_account_id UUID REFERENCES fis_account(account_id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_account_tenant ON fis_account(tenant_id);
CREATE INDEX idx_account_parent ON fis_account(parent_account_id);
CREATE INDEX idx_account_type ON fis_account(tenant_id, account_type);
```

---

## 3. Accounting Periods (`V12__create_accounting_periods.sql`)

```sql
CREATE TABLE fis_accounting_period (
    period_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    name VARCHAR(50) NOT NULL,              -- e.g., '2026-01', '2026-Q1'
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'SOFT_CLOSED', 'HARD_CLOSED')),
    closed_by VARCHAR(100),
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name),
    CHECK (end_date > start_date)
);

CREATE INDEX idx_period_tenant_status ON fis_accounting_period(tenant_id, status);
CREATE INDEX idx_period_dates ON fis_accounting_period(tenant_id, start_date, end_date);
```

---

## 4. Exchange Rates (`V13__create_exchange_rates.sql`)

Stores daily exchange rate snapshots for multi-currency support.

```sql
CREATE TABLE fis_exchange_rate (
    rate_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    source_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(18, 8) NOT NULL,           -- High precision for FX
    effective_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, source_currency, target_currency, effective_date)
);

CREATE INDEX idx_exchange_rate_lookup ON fis_exchange_rate(tenant_id, source_currency, target_currency, effective_date);
```

---

## 5. Idempotency Log (`V5__create_idempotency_log.sql`)

Persistent fallback store. Primary idempotency check happens in Redis; this is the durable record.

```sql
CREATE TABLE fis_idempotency_log (
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_id VARCHAR(255) NOT NULL,
    payload_hash VARCHAR(255) NOT NULL,
    response_body JSON NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, event_id)
);

CREATE INDEX idx_idempotency_tenant ON fis_idempotency_log(tenant_id, created_at);
```

---

## 6. Journal Entries (`V6__create_journal_entries.sql`)

The immutable ledger of all financial transactions.

```sql
CREATE TABLE fis_journal_entry (
    journal_entry_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_id VARCHAR(255) NOT NULL,
    posted_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    transaction_date DATE NOT NULL,
    description TEXT,
    reference_id VARCHAR(100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('POSTED', 'REVERSAL', 'CORRECTION')),
    reversal_of_id UUID REFERENCES fis_journal_entry(journal_entry_id),
    transaction_currency VARCHAR(3) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    exchange_rate NUMERIC(18, 8) NOT NULL DEFAULT 1.0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    previous_hash VARCHAR(255) NOT NULL,
    hash VARCHAR(255) NOT NULL,
    UNIQUE (tenant_id, event_id)
);

CREATE INDEX idx_je_tenant_date ON fis_journal_entry(tenant_id, posted_date);
CREATE INDEX idx_je_tenant_effective_date ON fis_journal_entry(tenant_id, effective_date);
CREATE INDEX idx_je_reference ON fis_journal_entry(tenant_id, reference_id);
CREATE INDEX idx_je_status ON fis_journal_entry(tenant_id, status);
CREATE INDEX idx_je_reversal ON fis_journal_entry(reversal_of_id);
CREATE UNIQUE INDEX uq_je_single_reversal
    ON fis_journal_entry(reversal_of_id)
    WHERE reversal_of_id IS NOT NULL;
```

Append-only rule: posted rows in `fis_journal_entry` and `fis_journal_line` are never updated or deleted by application logic.
In PostgreSQL, this is additionally enforced at the database level via triggers (see migration `V11__enforce_append_only_ledger`).

Multi-date behavior (`V20`):
- `effective_date` defaults from `posted_date` for existing and new rows when omitted by request.
- `transaction_date` defaults from `posted_date` for existing and new rows when omitted by request.
- Reporting queries use `effective_date` (with `posted_date` fallback for backward compatibility).

---

## 7. Journal Lines (`V7__create_journal_lines.sql`)

```sql
CREATE TABLE fis_journal_line (
    line_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    journal_entry_id UUID NOT NULL REFERENCES fis_journal_entry(journal_entry_id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES fis_account(account_id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    base_amount BIGINT NOT NULL,            -- Amount converted to base currency
    is_credit BOOLEAN NOT NULL,
    dimensions JSON,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jl_entry ON fis_journal_line(journal_entry_id);
CREATE INDEX idx_jl_account ON fis_journal_line(account_id);
```

**Implemented migration behavior:**
- `dimensions` is stored as `JSON` in the baseline migration.
- Hibernate maps this field through JSON JDBC type handling.

---

## 8. Mapping Rules Configuration (`V14__create_mapping_rules.sql`)

```sql
CREATE TABLE fis_mapping_rule (
    rule_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_type VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 1,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, event_type)
);

CREATE TABLE fis_mapping_rule_line (
    rule_line_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rule_id UUID NOT NULL REFERENCES fis_mapping_rule(rule_id) ON DELETE CASCADE,
    account_code_expression VARCHAR(255) NOT NULL,
    is_credit BOOLEAN NOT NULL,
    amount_expression VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_mapping_rule_event ON fis_mapping_rule(tenant_id, event_type);
```

---

## 9. Audit Log (`V15__create_audit_log_and_revaluation_runs.sql`)

Immutable log for tracking admin configuration changes.

```sql
CREATE TABLE fis_audit_log (
    audit_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    entity_type VARCHAR(50) NOT NULL,       -- e.g., 'MAPPING_RULE', 'ACCOUNT', 'PERIOD'
    entity_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('CREATED', 'UPDATED', 'DEACTIVATED', 'STATE_CHANGED')),
    old_value JSONB,
    new_value JSONB,
    performed_by VARCHAR(100) NOT NULL,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant_entity ON fis_audit_log(tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_time ON fis_audit_log(tenant_id, performed_at);
```

---

## 10. Outbox (`V10__create_outbox.sql`)

```sql
CREATE TABLE fis_outbox (
    outbox_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSON NOT NULL,
    traceparent VARCHAR(255),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_publish ON fis_outbox(published, created_at);
CREATE INDEX idx_outbox_tenant ON fis_outbox(tenant_id, created_at);
```

---

## 11. Append-Only Triggers (`V11__enforce_append_only_ledger.sql`)

`fis_journal_entry` and `fis_journal_line` are protected by PostgreSQL triggers that reject all `UPDATE` and `DELETE` operations.

---

## 12. Auto-Reversal Flag (`V19__add_auto_reverse_to_journal_entry.sql`)

```sql
ALTER TABLE fis_journal_entry
    ADD COLUMN auto_reverse BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_journal_auto_reverse
    ON fis_journal_entry(tenant_id, auto_reverse, posted_date)
    WHERE auto_reverse = TRUE;
```

---

## 13. Reporting Performance Indexes (`V23__add_reporting_performance_indexes.sql`)

Added to protect heavy reporting queries as dataset volume grows.

```sql
CREATE INDEX IF NOT EXISTS idx_je_tenant_status_effective_date
    ON fis_journal_entry (tenant_id, status, effective_date);

CREATE INDEX IF NOT EXISTS idx_je_tenant_status_effective_seq
    ON fis_journal_entry (tenant_id, status, effective_date, sequence_number);

CREATE INDEX IF NOT EXISTS idx_jl_account_entry
    ON fis_journal_line (account_id, journal_entry_id);

CREATE INDEX IF NOT EXISTS idx_account_tenant_code_active
    ON fis_account (tenant_id, code, is_active);

CREATE INDEX IF NOT EXISTS idx_account_tenant_type_active
    ON fis_account (tenant_id, account_type, is_active);
```

Reporting SQL now filters directly on `effective_date` to ensure these indexes are usable.

---

## 13. Journal Workflow Multi-Date Support (`V21__add_effective_and_transaction_dates_to_journal_workflow.sql`)

```sql
ALTER TABLE fis_journal_workflow
    ADD COLUMN effective_date DATE NOT NULL,
    ADD COLUMN transaction_date DATE NOT NULL;

CREATE INDEX idx_workflow_tenant_effective_date
    ON fis_journal_workflow (tenant_id, effective_date);
```

Draft workflow rows now preserve accounting dates (`effective_date`, `transaction_date`) through maker-checker approval.

---

## 14. Functional Translation Runs (`V22__create_period_translation_run.sql`)

```sql
CREATE TABLE fis_period_translation_run (
    run_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    period_id UUID NOT NULL REFERENCES fis_accounting_period(period_id),
    event_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    details JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_translation_run_tenant_period ON fis_period_translation_run(tenant_id, period_id);
```

Used by R16 functional-currency translation to track period-level idempotent execution and generated CTA journal entries.

---

## Immutability Model (Implemented)

- **Immutable append-only tables (strict):**
  - `fis_journal_entry`
  - `fis_journal_line`
  - Enforced by DB triggers in PostgreSQL to block `UPDATE`/`DELETE`.

- **Mutable derived/projection table (by design):**
  - `fis_account.current_balance` is updated transactionally for read performance.
  - Source-of-truth remains the append-only journal.
