CREATE TABLE fis_exchange_rate (
    rate_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    source_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(18, 8) NOT NULL,
    effective_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, source_currency, target_currency, effective_date)
);

CREATE INDEX idx_exchange_rate_lookup
    ON fis_exchange_rate(tenant_id, source_currency, target_currency, effective_date);
