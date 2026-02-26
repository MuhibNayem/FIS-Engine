ALTER TABLE fis_account
    ADD COLUMN is_contra BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_account_tenant_contra ON fis_account(tenant_id, is_contra);
