# Citus Distributed Table Strategy

## Overview

This document describes how FIS-Engine tables are distributed across Citus workers for horizontal scaling.

## Distribution Model

### Shard Key Selection

| Table | Distribution Column | Distribution Method | Rationale |
|-------|-------------------|---------------------|-----------|
| `fis_journal_entry` | `tenant_id` | Hash | Entries co-located per tenant |
| `fis_journal_line` | `journal_entry_id` | Hash | Lines co-located with parent entry |
| `fis_account` | `tenant_id` | Reference | Small, frequently joined |
| `fis_business_entity` | `tenant_id` | Reference | Master tenant data |
| `fis_accounting_period` | `tenant_id` | Reference | Period metadata |

### Account Range Sharding (Alternative)

For account-range based sharding (as specified in requirements):

```
Shard 1: Accounts 1000-4999  (Worker 1)
Shard 2: Accounts 5000-9999  (Worker 2)
Shard 3: Accounts 10000+     (Worker 3)
```

This is achieved via Citus' `create_distributed_table` with `hash` distribution
and proper shard routing at the application layer.

## Colocation Strategy

### Journal Entry + Line Co-location

`fis_journal_line` is distributed by `journal_entry_id` (the FK to journal_entry).
This ensures that all lines for a journal entry live on the same worker as
the entry itself, minimizing cross-node joins.

```sql
-- After creating fis_journal_entry distributed
SELECT create_distributed_table('fis_journal_line', 'journal_entry_id', 'hash');
SELECT create_colocated_table('fis_journal_line', 'fis_journal_entry', 'journal_entry_id');
```

### Reference Tables

Reference tables are replicated to all workers for O(1) lookups:

```sql
SELECT create_reference_table('fis_account');
SELECT create_reference_table('fis_business_entity');
SELECT create_reference_table('fis_accounting_period');
```

## Shard Routing at Application Layer

The application layer uses `ShardRoutingService` to determine which shard
handles each request based on `tenant_id` or `account_code`. The Citus
coordinator receives the query and routes it to appropriate workers.

```java
// Application determines shard
Shard shard = shardRouter.getShardForTenant(tenantId);

// All queries go through Citus coordinator
// Citus routes to correct worker based on distribution column
```

## Query Routing

### Local Queries (Fast)

Queries filtered on distribution column execute locally on single worker:

```sql
-- Goes directly to worker owning tenant_id=xxx
SELECT * FROM fis_journal_entry WHERE tenant_id = 'xxx';
```

### Distributed Queries (Slower)

Queries without distribution column filter scan all workers:

```sql
-- Broadcasts to all workers, results merged
SELECT * FROM fis_journal_entry WHERE posted_date >= '2026-01-01';
```

### Cross-Node Joins

Joins between distributed tables on non-distribution columns require
shuffling data between workers:

```sql
-- Executes on all workers, results aggregated
SELECT je.*, jl.* FROM fis_journal_entry je
JOIN fis_journal_line jl ON je.journal_entry_id = jl.journal_entry_id
WHERE je.tenant_id = 'xxx';
```

## DDL Considerations

### Adding Columns

```sql
-- Safe: Adding columns to distributed tables
ALTER TABLE fis_journal_entry ADD COLUMN IF NOT EXISTS new_column TEXT;
-- Propagates to all shards automatically
```

### Adding Indexes

```sql
-- Create index on coordinator, Citus propagates to all workers
CREATE INDEX idx_je_tenant_id ON fis_journal_entry(tenant_id);
```

### Adding Foreign Keys

```sql
-- Reference tables: FK to reference table (replicated) is fast
-- Distributed tables: FK to distributed table triggers cross-node validation
ALTER TABLE fis_journal_line ADD FOREIGN KEY (account_id) REFERENCES fis_account(account_id);
```

## Transaction Isolation

Citus uses 2PC (two-phase commit) for distributed transactions:

```properties
citus.multi_shard_commit_protocol = '2pc'
```

This provides SERIALIZABLE isolation for multi-shard transactions but
adds latency. Consider batching operations within a single shard
to avoid distributed transactions.

## Memory Configuration

Per worker:
- `shared_buffers`: 256MB minimum
- `work_mem`: 16-32MB for sort operations
- `maintenance_work_mem`: 128MB for DDL

Coordinator:
- `shared_buffers`: 512MB (handles query planning for all workers)
- `work_mem`: 32MB

## Monitoring

```sql
-- Check shard distribution
SELECT * FROM citus_shards;

-- Check worker health
SELECT * FROM citus_get_active_worker_nodes();

-- Check distributed table sizes
SELECT * FROM citus_table_size_utilization;

-- Check long-running transactions
SELECT * FROM citus_ddl_atoms;
```