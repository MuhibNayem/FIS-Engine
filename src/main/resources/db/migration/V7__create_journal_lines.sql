CREATE TABLE fis_journal_line (
    line_id UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES fis_journal_entry(journal_entry_id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES fis_account(account_id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    base_amount BIGINT NOT NULL,
    is_credit BOOLEAN NOT NULL,
    dimensions JSON,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_jl_entry ON fis_journal_line(journal_entry_id);
CREATE INDEX idx_jl_account ON fis_journal_line(account_id);
