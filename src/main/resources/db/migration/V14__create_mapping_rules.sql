CREATE TABLE fis_mapping_rule (
    rule_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_type VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 1,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, event_type)
);

CREATE TABLE fis_mapping_rule_line (
    rule_line_id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES fis_mapping_rule(rule_id),
    account_code_expression VARCHAR(255) NOT NULL,
    is_credit BOOLEAN NOT NULL,
    amount_expression VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_mapping_rule_tenant_event ON fis_mapping_rule(tenant_id, event_type, is_active);
CREATE INDEX idx_mapping_rule_line_rule_order ON fis_mapping_rule_line(rule_id, sort_order);
