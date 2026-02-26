CREATE TABLE fis_journal_workflow (
    workflow_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    event_id VARCHAR(255) NOT NULL,
    posted_date DATE NOT NULL,
    description TEXT,
    reference_id VARCHAR(100),
    transaction_currency VARCHAR(3) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    submitted_by VARCHAR(100),
    submitted_at TIMESTAMP WITH TIME ZONE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    rejected_by VARCHAR(100),
    rejected_at TIMESTAMP WITH TIME ZONE,
    rejection_reason TEXT,
    posted_journal_entry_id UUID REFERENCES fis_journal_entry(journal_entry_id),
    traceparent VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, event_id)
);

CREATE TABLE fis_journal_workflow_line (
    workflow_line_id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES fis_journal_workflow(workflow_id) ON DELETE CASCADE,
    account_code VARCHAR(50) NOT NULL,
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    is_credit BOOLEAN NOT NULL,
    dimensions JSON,
    sort_order INTEGER NOT NULL
);

CREATE INDEX idx_workflow_tenant_status_created
    ON fis_journal_workflow (tenant_id, status, created_at DESC);

CREATE INDEX idx_workflow_line_workflow
    ON fis_journal_workflow_line (workflow_id, sort_order);
