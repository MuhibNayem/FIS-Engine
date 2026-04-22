-- V35__citus_sharding_setup.sql
-- Citus Sharding Configuration for Account-Range Based Distribution

-- This migration configures Citus for account-range based sharding
-- Shard 1: Accounts 1000-4999
-- Shard 2: Accounts 5000-9999
-- Shard 3: Accounts 10000+

-- Create shard分区映射表 (for application-level routing)
CREATE TABLE IF NOT EXISTS fis_shard_mapping (
    shard_id BIGSERIAL PRIMARY KEY,
    shard_name VARCHAR(50) NOT NULL UNIQUE,
    account_range_start INTEGER NOT NULL,
    account_range_end INTEGER NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shard_mapping_range ON fis_shard_mapping(account_range_start, account_range_end);
CREATE INDEX idx_shard_mapping_active ON fis_shard_mapping(is_active);

-- Initialize default shard mappings
INSERT INTO fis_shard_mapping (shard_name, account_range_start, account_range_end, description)
VALUES
    ('SHARD_1', 1000, 4999, 'Assets, Receivables (Accounts 1000-4999)'),
    ('SHARD_2', 5000, 9999, 'Liabilities, Payables (Accounts 5000-9999)'),
    ('SHARD_3', 10000, 2147483647, 'Equity, Revenue, Expenses (Accounts 10000+)')
ON CONFLICT (shard_name) DO NOTHING;

-- Create Citus extension (requires superuser)
-- Note: This is typically run as a separate setup step, not in Flyway
-- The extension must be created before distributed tables can be created

-- Create a function to determine shard for an account
CREATE OR REPLACE FUNCTION fis_get_shard_for_account(p_account_code TEXT)
RETURNS BIGINT AS $$
DECLARE
    v_account_num INTEGER;
    v_shard_id BIGINT;
BEGIN
    -- Extract numeric part from account code
    v_account_num := CAST(REGEXP_REPLACE(p_account_code, '[^0-9]', '', 'g') AS INTEGER);

    SELECT shard_id INTO v_shard_id
    FROM fis_shard_mapping
    WHERE is_active = true
      AND v_account_num >= account_range_start
      AND v_account_num <= account_range_end
    LIMIT 1;

    RETURN v_shard_id;
END;
$$ LANGUAGE plpgsql STABLE;

-- Create a function to get shard for tenant (hash-based fallback)
CREATE OR REPLACE FUNCTION fis_get_shard_for_tenant(p_tenant_id UUID)
RETURNS BIGINT AS $$
DECLARE
    v_hash INTEGER;
    v_shard_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_shard_count FROM fis_shard_mapping WHERE is_active = true;
    IF v_shard_count = 0 THEN
        RETURN 1;
    END IF;

    v_hash := ABS(hashtext(p_tenant_id::TEXT));
    RETURN ((v_hash % v_shard_count) + 1)::BIGINT;
END;
$$ LANGUAGE plpgsql STABLE;

-- Create distributed table setup functions (run after Citus extension is enabled)
CREATE OR REPLACE FUNCTION fis_setup_distributed_table(
    p_table_name TEXT,
    p_distribution_column TEXT,
    p_table_type TEXT DEFAULT 'distributed'
)
RETURNS BOOLEAN AS $$
DECLARE
    v_sql TEXT;
BEGIN
    IF p_table_type = 'distributed' THEN
        v_sql := format('SELECT create_distributed_table(%L, %L, ''hash'');', p_table_name, p_distribution_column);
    ELSIF p_table_type = 'reference' THEN
        v_sql := format('SELECT create_reference_table(%L);', p_table_name);
    ELSIF p_table_type = 'colocated' THEN
        v_sql := format('SELECT create_distributed_table(%L, %L, ''hash'', colocate_with => ''fis_journal_entry'');',
                       p_table_name, p_distribution_column);
    END IF;

    EXECUTE v_sql;
    RETURN true;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Could not setup distributed table %: %', p_table_name, SQLERRM;
        RETURN false;
END;
$$ LANGUAGE plpgsql;

-- Create function to setup all distributed tables
CREATE OR REPLACE FUNCTION fis_setup_all_distributed_tables()
RETURNS VOID AS $$
BEGIN
    -- Core journal tables
    PERFORM fis_setup_distributed_table('fis_journal_entry', 'tenant_id', 'distributed');
    PERFORM fis_setup_distributed_table('fis_journal_line', 'journal_entry_id', 'colocated');
    PERFORM fis_setup_distributed_table('fis_account_balance', 'account_id', 'colocated');

    -- Reference tables (replicated on all nodes)
    PERFORM fis_setup_distributed_table('fis_account', 'tenant_id', 'reference');
    PERFORM fis_setup_distributed_table('fis_business_entity', 'tenant_id', 'reference');
    PERFORM fis_setup_distributed_table('fis_accounting_period', 'tenant_id', 'reference');
    PERFORM fis_setup_distributed_table('fis_exchange_rate', 'tenant_id', 'reference');

    RAISE NOTICE 'Distributed table setup complete';
END;
$$ LANGUAGE plpgsql;

-- Create view for shard information
CREATE OR REPLACE VIEW fis_shard_info AS
SELECT
    s.shard_id,
    s.shard_name,
    s.account_range_start,
    s.account_range_end,
    s.description,
    s.is_active,
    w.node_name,
    w.node_port,
    w.is_active AS worker_active
FROM fis_shard_mapping s
LEFT JOIN LATERAL (
    SELECT node_name, node_port, is_active
    FROM citus_get_active_worker_nodes()
    WHERE shard_id = s.shard_id
) w ON true;

-- Create metadata table for tracking distributed table status
CREATE TABLE IF NOT EXISTS fis_distributed_table_metadata (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL UNIQUE,
    distribution_column VARCHAR(100),
    table_type VARCHAR(20) NOT NULL, -- 'distributed', 'reference', 'colocated'
    is_setup_complete BOOLEAN DEFAULT false,
    setup_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Track journal entry distribution by account range
CREATE OR REPLACE FUNCTION fis_distribute_journal_entry_by_account_range()
RETURNS TRIGGER AS $$
DECLARE
    v_account_code TEXT;
    v_shard_id BIGINT;
BEGIN
    -- This trigger function would be used to route entries to appropriate shards
    -- based on the account codes in the journal lines
    -- For now, we use tenant-based hashing which is handled by Citus

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Add comments for documentation
COMMENT ON TABLE fis_shard_mapping IS 'Shard configuration for account-range based distribution';
COMMENT ON FUNCTION fis_get_shard_for_account IS 'Determines which shard an account belongs to based on account code';
COMMENT ON FUNCTION fis_get_shard_for_tenant IS 'Determines which shard a tenant belongs to using hash-based distribution';
COMMENT ON TABLE fis_distributed_table_metadata IS 'Tracks the setup status of distributed tables in Citus';