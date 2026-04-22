-- V38__citus_colocate_tables.sql
-- Configure table colocation for optimal query performance
-- Must be run after V37__citus_create_distributed_tables.sql

-- Enable Citus extension (idempotent)
CREATE EXTENSION IF NOT EXISTS citus;

-- Create colocation group for journal entries and related tables
-- This ensures related data is on the same worker

-- Colocate fis_journal_line with fis_journal_entry
-- (they are already colocated via journal_entry_id distribution,
-- but this makes the relationship explicit)

SELECT create_colocated_table(
    'fis_journal_line',
    'fis_journal_entry',
    'journal_entry_id'
);

-- Colocate account balance updates with accounts
SELECT create_colocated_table(
    'fis_account_balance',
    'fis_account',
    'account_id'
);

-- Verify colocation
DO $$
DECLARE
    v_colocation_size BIGINT;
BEGIN
    -- Check colocation group for journal tables
    SELECT COUNT(*) INTO v_colocation_size
    FROM citus_table_node_policies
    WHERE table_name = 'fis_journal_line'
    AND colocation_order = 1;

    IF v_colocation_size > 0 THEN
        RAISE NOTICE 'Journal tables are properly colocated';
    ELSE
        RAISE WARNING 'Journal tables may not be colocated properly';
    END IF;
END;
$$;

-- Update metadata
UPDATE fis_distributed_table_metadata
SET is_setup_complete = true, updated_at = CURRENT_TIMESTAMP
WHERE table_name IN ('fis_journal_line', 'fis_account_balance');

-- Create shard distribution view for monitoring
CREATE OR REPLACE VIEW fis_shard_distribution AS
SELECT
    logicalrelid::regclass::text AS table_name,
    shardid,
    shardname,
    citus_get_shard_id_for_distribution_column(logicalrelid, distribution_column) AS shard_num,
    nodename,
    nodeport,
    shard_size
FROM citus_shards
ORDER BY logicalrelid::regclass::text, shardid;

-- Create view for monitoring worker health
CREATE OR REPLACE VIEW fis_worker_health AS
SELECT
    node_name,
    node_port,
    is_active,
    last_status_update,
    metadata->>'shard_count' AS shard_count,
    metadata->>'total_connections' AS total_connections
FROM citus_get_active_worker_nodes()
CROSS JOIN LATERAL jsonb_each_object(metadata) AS m(key, value)
WHERE m.key IN ('shard_count', 'total_connections');

-- Grant appropriate permissions
GRANT SELECT ON fis_shard_distribution TO PUBLIC;
GRANT SELECT ON fis_worker_health TO PUBLIC;

COMMENT ON VIEW fis_shard_distribution IS 'Shows how tables are distributed across shards';
COMMENT ON VIEW fis_worker_health IS 'Shows Citus worker node health status';