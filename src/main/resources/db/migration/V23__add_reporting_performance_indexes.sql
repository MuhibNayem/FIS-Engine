-- Performance indexes for reporting-heavy access patterns (P2.9)

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
