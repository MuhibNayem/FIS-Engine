-- Add retry tracking, DLQ flags, and error context to the outbox table.
ALTER TABLE fis_outbox
    ADD COLUMN IF NOT EXISTS retry_count   INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_retries   INTEGER      NOT NULL DEFAULT 50,
    ADD COLUMN IF NOT EXISTS dlq           BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_error    TEXT;

-- Index for DLQ queries (events that have exhausted retries but not yet discarded)
CREATE INDEX IF NOT EXISTS idx_outbox_dlq ON fis_outbox(dlq, published, created_at)
    WHERE dlq = TRUE;

-- Index for unpublished events ordered by retry count (helps identify stale retries)
CREATE INDEX IF NOT EXISTS idx_outbox_retry ON fis_outbox(published, retry_count, created_at)
    WHERE published = FALSE AND dlq = FALSE;
