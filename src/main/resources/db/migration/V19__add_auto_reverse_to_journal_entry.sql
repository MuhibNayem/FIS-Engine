-- V19: Add auto_reverse flag to journal entries for automatic accrual reversals
ALTER TABLE fis_journal_entry ADD COLUMN auto_reverse BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for efficient lookup of auto-reversible entries
CREATE INDEX idx_journal_auto_reverse ON fis_journal_entry(tenant_id, auto_reverse, posted_date)
    WHERE auto_reverse = TRUE;
