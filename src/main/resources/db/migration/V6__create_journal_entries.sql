CREATE TABLE fis_journal_entry (
    journal_entry_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_id VARCHAR(255) NOT NULL,
    posted_date DATE NOT NULL,
    description TEXT,
    reference_id VARCHAR(100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('POSTED', 'REVERSAL', 'CORRECTION')),
    reversal_of_id UUID REFERENCES fis_journal_entry(journal_entry_id),
    transaction_currency VARCHAR(3) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    exchange_rate NUMERIC(18, 8) NOT NULL DEFAULT 1.0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    previous_hash VARCHAR(255) NOT NULL,
    hash VARCHAR(255) NOT NULL,
    UNIQUE (tenant_id, event_id)
);
-- NOTE: FK (tenant_id, event_id) -> fis_idempotency_log deferred to Phase 3

CREATE INDEX idx_je_tenant_date ON fis_journal_entry(tenant_id, posted_date);
CREATE INDEX idx_je_reference ON fis_journal_entry(tenant_id, reference_id);
CREATE INDEX idx_je_status ON fis_journal_entry(tenant_id, status);
CREATE INDEX idx_je_reversal ON fis_journal_entry(reversal_of_id);
