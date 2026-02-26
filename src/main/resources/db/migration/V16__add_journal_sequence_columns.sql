ALTER TABLE fis_journal_entry
    ADD COLUMN fiscal_year INTEGER,
    ADD COLUMN sequence_number BIGINT;

UPDATE fis_journal_entry
SET fiscal_year = EXTRACT(YEAR FROM posted_date)::INTEGER
WHERE fiscal_year IS NULL;

WITH numbered AS (
    SELECT journal_entry_id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id, fiscal_year
               ORDER BY created_at, journal_entry_id
           ) AS seq_num
    FROM fis_journal_entry
)
UPDATE fis_journal_entry je
SET sequence_number = numbered.seq_num
FROM numbered
WHERE je.journal_entry_id = numbered.journal_entry_id
  AND je.sequence_number IS NULL;

ALTER TABLE fis_journal_entry
    ALTER COLUMN fiscal_year SET NOT NULL,
    ALTER COLUMN sequence_number SET NOT NULL;

CREATE UNIQUE INDEX uq_je_tenant_fiscal_year_sequence
    ON fis_journal_entry (tenant_id, fiscal_year, sequence_number);

CREATE TABLE fis_journal_sequence (
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    fiscal_year INTEGER NOT NULL,
    next_value BIGINT NOT NULL CHECK (next_value > 0),
    PRIMARY KEY (tenant_id, fiscal_year)
);

INSERT INTO fis_journal_sequence (tenant_id, fiscal_year, next_value)
SELECT tenant_id, fiscal_year, MAX(sequence_number) + 1
FROM fis_journal_entry
GROUP BY tenant_id, fiscal_year
ON CONFLICT (tenant_id, fiscal_year) DO NOTHING;
