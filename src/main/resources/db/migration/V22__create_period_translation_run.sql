CREATE TABLE fis_period_translation_run (
    run_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    period_id UUID NOT NULL REFERENCES fis_accounting_period(period_id),
    event_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    details JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_translation_run_tenant_period ON fis_period_translation_run(tenant_id, period_id);
