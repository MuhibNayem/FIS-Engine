CREATE TABLE fis_accounting_period (
    period_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'SOFT_CLOSED', 'HARD_CLOSED')),
    closed_by VARCHAR(100),
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, name),
    CHECK (end_date > start_date)
);

CREATE INDEX idx_period_tenant_status ON fis_accounting_period(tenant_id, status);
CREATE INDEX idx_period_dates ON fis_accounting_period(tenant_id, start_date, end_date);
