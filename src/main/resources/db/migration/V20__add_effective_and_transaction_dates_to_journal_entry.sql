-- V20: Add effective_date and transaction_date to journal entries
ALTER TABLE fis_journal_entry
    ADD COLUMN effective_date DATE,
    ADD COLUMN transaction_date DATE;

UPDATE fis_journal_entry
SET effective_date = posted_date,
    transaction_date = posted_date
WHERE effective_date IS NULL
   OR transaction_date IS NULL;

ALTER TABLE fis_journal_entry
    ALTER COLUMN effective_date SET NOT NULL,
    ALTER COLUMN transaction_date SET NOT NULL;

CREATE INDEX idx_je_tenant_effective_date
    ON fis_journal_entry(tenant_id, effective_date);
