CREATE TABLE fis_bank_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    branch_code VARCHAR(20),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    gl_account_code VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, account_number)
);
CREATE INDEX idx_bank_account_tenant ON fis_bank_account(tenant_id);

CREATE TABLE fis_bank_statement (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    bank_account_id UUID NOT NULL REFERENCES fis_bank_account(id),
    statement_date DATE NOT NULL,
    opening_balance BIGINT NOT NULL DEFAULT 0,
    closing_balance BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'IMPORTED' CHECK (status IN ('IMPORTED','RECONCILING','RECONCILED')),
    imported_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_bank_stmt_tenant_account ON fis_bank_statement(tenant_id, bank_account_id);

CREATE TABLE fis_bank_statement_line (
    id UUID PRIMARY KEY,
    statement_id UUID NOT NULL REFERENCES fis_bank_statement(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    amount BIGINT NOT NULL,
    reference VARCHAR(100),
    matched BOOLEAN NOT NULL DEFAULT false,
    matched_journal_line_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_bank_stmt_line_stmt ON fis_bank_statement_line(statement_id);
CREATE INDEX idx_bank_stmt_line_matched ON fis_bank_statement_line(statement_id, matched);

CREATE TABLE fis_reconciliation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reconciled_at TIMESTAMP WITH TIME ZONE,
    reconciled_by VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS','COMPLETED')),
    total_matched BIGINT DEFAULT 0,
    total_unmatched BIGINT DEFAULT 0,
    discrepancy BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_recon_tenant_account ON fis_reconciliation(tenant_id, bank_account_id);

CREATE TABLE fis_reconciliation_match (
    id UUID PRIMARY KEY,
    reconciliation_id UUID NOT NULL REFERENCES fis_reconciliation(id),
    statement_line_id UUID NOT NULL,
    journal_line_id UUID,
    amount BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_recon_match_recon ON fis_reconciliation_match(reconciliation_id);
CREATE INDEX idx_recon_match_stmt_line ON fis_reconciliation_match(statement_line_id);
