CREATE TABLE fis_account (
    account_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency_code VARCHAR(3) NOT NULL,
    current_balance BIGINT NOT NULL DEFAULT 0,
    parent_account_id UUID REFERENCES fis_account(account_id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_account_tenant ON fis_account(tenant_id);
CREATE INDEX idx_account_parent ON fis_account(parent_account_id);
CREATE INDEX idx_account_type ON fis_account(tenant_id, account_type);
