-- V21: Add effective_date and transaction_date to journal workflow drafts
ALTER TABLE fis_journal_workflow
    ADD COLUMN effective_date DATE,
    ADD COLUMN transaction_date DATE;

UPDATE fis_journal_workflow
SET effective_date = posted_date,
    transaction_date = posted_date
WHERE effective_date IS NULL
   OR transaction_date IS NULL;

ALTER TABLE fis_journal_workflow
    ALTER COLUMN effective_date SET NOT NULL,
    ALTER COLUMN transaction_date SET NOT NULL;

CREATE INDEX idx_workflow_tenant_effective_date
    ON fis_journal_workflow (tenant_id, effective_date);
