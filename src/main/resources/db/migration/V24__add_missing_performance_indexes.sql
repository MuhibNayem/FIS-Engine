-- Add missing performance indexes for common query patterns

-- Workflow event_id lookup (for idempotency checks)
CREATE INDEX idx_workflow_tenant_event
    ON fis_journal_workflow (tenant_id, event_id);

-- Accounting period overlap queries (for period validation and year-end close)
CREATE INDEX idx_period_tenant_dates
    ON fis_accounting_period (tenant_id, start_date, end_date);

-- Journal line account lookup (for reporting and balance queries)
CREATE INDEX idx_jl_entry_account
    ON fis_journal_line (journal_entry_id, account_id);

-- Idempotency log lookup with status filtering
CREATE INDEX idx_idempotency_tenant_status
    ON fis_idempotency_log (tenant_id, event_id, status);

-- Outbox unpublished events for relay processing
CREATE INDEX idx_outbox_published_created
    ON fis_outbox (published, created_at)
    WHERE published = false;

-- Exchange rate lookup by date range
CREATE INDEX idx_exchange_rate_tenant_date
    ON fis_exchange_rate (tenant_id, source_currency, target_currency, effective_date);

-- Mapping rule by event type (for event ingestion)
CREATE INDEX idx_mapping_rule_tenant_type_active
    ON fis_mapping_rule (tenant_id, event_type)
    WHERE is_active = true;

-- Revaluation run by tenant and period
CREATE INDEX idx_revaluation_run_tenant_period
    ON fis_period_revaluation_run (tenant_id, period_id);
