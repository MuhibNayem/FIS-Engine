CREATE TABLE fis_outbox (
    outbox_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSON NOT NULL,
    traceparent VARCHAR(255),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_outbox_publish ON fis_outbox(published, created_at);
CREATE INDEX idx_outbox_tenant ON fis_outbox(tenant_id, created_at);
