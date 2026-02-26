CREATE TABLE fis_audit_log (
    audit_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    performed_by VARCHAR(100) NOT NULL,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant_entity ON fis_audit_log(tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_tenant_time ON fis_audit_log(tenant_id, performed_at DESC);

CREATE TABLE fis_period_revaluation_run (
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

CREATE UNIQUE INDEX uq_revaluation_run_tenant_period ON fis_period_revaluation_run(tenant_id, period_id);
