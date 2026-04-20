CREATE TABLE fis_budget (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL, fiscal_year INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_budget_tenant_year ON fis_budget(tenant_id, fiscal_year);
CREATE TABLE fis_budget_line (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    budget_id UUID NOT NULL REFERENCES fis_budget(id),
    account_code VARCHAR(50) NOT NULL, department VARCHAR(100),
    month VARCHAR(7) NOT NULL, budgeted_amount BIGINT NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD'
);
CREATE INDEX idx_budget_line_budget ON fis_budget_line(budget_id);
CREATE TABLE fis_budget_transfer (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    budget_id UUID NOT NULL, from_account_code VARCHAR(50) NOT NULL,
    to_account_code VARCHAR(50) NOT NULL, amount BIGINT NOT NULL,
    reason VARCHAR(500), approved_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
