CREATE TABLE fis_asset_category (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    default_useful_life_months INTEGER NOT NULL,
    depreciation_method VARCHAR(30) NOT NULL DEFAULT 'STRAIGHT_LINE',
    gl_account_code VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE fis_fixed_asset (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    category_id UUID NOT NULL REFERENCES fis_asset_category(id),
    asset_tag VARCHAR(50) NOT NULL, name VARCHAR(200) NOT NULL,
    acquisition_date DATE NOT NULL, acquisition_cost BIGINT NOT NULL,
    salvage_value BIGINT, useful_life_months INTEGER NOT NULL,
    depreciation_method VARCHAR(30) NOT NULL,
    accumulated_depreciation BIGINT DEFAULT 0,
    net_book_value BIGINT, location VARCHAR(200),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    disposal_date DATE, disposal_proceeds BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, asset_tag)
);
CREATE INDEX idx_fixed_asset_tenant ON fis_fixed_asset(tenant_id);
CREATE TABLE fis_asset_depreciation_run (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    period VARCHAR(7) NOT NULL, run_date DATE NOT NULL,
    total_depreciation BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE fis_asset_disposal (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL, disposal_date DATE NOT NULL,
    sale_proceeds BIGINT, net_book_value BIGINT NOT NULL,
    gain_loss BIGINT NOT NULL, disposal_type VARCHAR(20) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
