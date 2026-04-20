CREATE TABLE fis_consolidation_group (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL, description VARCHAR(500),
    base_currency VARCHAR(3) DEFAULT 'USD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE fis_consolidation_member (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    group_id UUID NOT NULL REFERENCES fis_consolidation_group(id),
    ownership_percentage NUMERIC(5,2), currency VARCHAR(3),
    translation_method VARCHAR(30) DEFAULT 'CLOSING'
);
CREATE INDEX idx_consolidation_member_group ON fis_consolidation_member(group_id);
CREATE TABLE fis_consolidation_run (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    group_id UUID NOT NULL, period VARCHAR(7) NOT NULL,
    run_date DATE NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    total_assets BIGINT, total_liabilities BIGINT, total_equity BIGINT,
    net_income BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE fis_elimination_rule (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    group_id UUID NOT NULL, from_account_code VARCHAR(50) NOT NULL,
    to_account_code VARCHAR(50) NOT NULL, description VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE fis_elimination_entry (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    run_id UUID NOT NULL REFERENCES fis_consolidation_run(id),
    from_tenant_id UUID NOT NULL, to_tenant_id UUID,
    account_code VARCHAR(50) NOT NULL, amount BIGINT NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
