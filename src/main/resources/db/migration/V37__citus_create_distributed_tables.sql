-- V37__citus_create_distributed_tables.sql
-- Create distributed tables for FIS journal entries
-- Must be run after V36__citus_enable_extension.sql

-- Enable Citus extension (idempotent)
CREATE EXTENSION IF NOT EXISTS citus;

-- Setup function for distributed tables
CREATE OR REPLACE FUNCTION setup_fis_distributed_tables()
RETURNS void AS $$
BEGIN
    -- Distribute fis_journal_entry by tenant_id
    -- Each tenant's entries are co-located on same worker
    PERFORM create_distributed_table(
        'fis_journal_entry',
        'tenant_id',
        'hash',
        'none'
    );

    -- Distribute fis_journal_line by journal_entry_id
    -- Lines co-located with parent journal entry
    PERFORM create_distributed_table(
        'fis_journal_line',
        'journal_entry_id',
        'hash',
        'fis_journal_entry'
    );

    -- Account balances co-located with accounts
    PERFORM create_distributed_table(
        'fis_account_balance',
        'account_id',
        'hash',
        'fis_account'
    );

    -- Reference tables (replicated on all workers)
    PERFORM create_reference_table('fis_account');
    PERFORM create_reference_table('fis_business_entity');
    PERFORM create_reference_table('fis_accounting_period');
    PERFORM create_reference_table('fis_exchange_rate');
    PERFORM create_reference_table('fis_mapping_rule');
    PERFORM create_reference_table('fis_journal_sequence');

    RAISE NOTICE 'FIS distributed tables setup complete';
END;
$$ LANGUAGE plpgsql;

-- Execute the setup
SELECT setup_fis_distributed_tables();

-- Create indexes on distributed tables
-- These will be created on all workers

-- Index on tenant_id for fast tenant-scoped queries
CREATE INDEX IF NOT EXISTS idx_je_tenant_id ON fis_journal_entry(tenant_id);

-- Index on posted_date for date-range queries
CREATE INDEX IF NOT EXISTS idx_je_posted_date ON fis_journal_entry(posted_date);

-- Index on fiscal_year for fiscal queries
CREATE INDEX IF NOT EXISTS idx_je_fiscal_year ON fis_journal_entry(fiscal_year);

-- Index on sequence_number for hash chain
CREATE INDEX IF NOT EXISTS idx_je_sequence ON fis_journal_entry(tenant_id, fiscal_year, sequence_number);

-- Index on tenant_id + event_id for idempotency
CREATE INDEX IF NOT EXISTS idx_je_tenant_event ON fis_journal_entry(tenant_id, event_id);

-- Index on journal_entry_id for line lookups
CREATE INDEX IF NOT EXISTS idx_jl_entry_id ON fis_journal_line(journal_entry_id);

-- Index on account_id for balance lookups
CREATE INDEX IF NOT EXISTS idx_jl_account_id ON fis_journal_line(account_id);

-- Create metadata entry for distributed table tracking
INSERT INTO fis_distributed_table_metadata (table_name, distribution_column, table_type, is_setup_complete)
VALUES
    ('fis_journal_entry', 'tenant_id', 'distributed', true),
    ('fis_journal_line', 'journal_entry_id', 'colocated', true),
    ('fis_account_balance', 'account_id', 'colocated', true),
    ('fis_account', 'tenant_id', 'reference', true),
    ('fis_business_entity', 'tenant_id', 'reference', true),
    ('fis_accounting_period', 'tenant_id', 'reference', true)
ON CONFLICT (table_name) DO UPDATE SET
    is_setup_complete = true,
    updated_at = CURRENT_TIMESTAMP;