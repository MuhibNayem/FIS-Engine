CREATE TABLE fis_idempotency_log (
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_id VARCHAR(255) NOT NULL,
    payload_hash VARCHAR(255) NOT NULL,
    response_body JSON NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, event_id)
);

CREATE INDEX idx_idempotency_tenant ON fis_idempotency_log(tenant_id, created_at);
