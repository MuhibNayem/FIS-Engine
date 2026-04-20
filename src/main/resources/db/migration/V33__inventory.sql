CREATE TABLE fis_warehouse (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL, name VARCHAR(200) NOT NULL,
    location VARCHAR(300), gl_account_code VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_warehouse_tenant ON fis_warehouse(tenant_id);
CREATE TABLE fis_inventory_item (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL, name VARCHAR(200) NOT NULL,
    category VARCHAR(100), uom VARCHAR(20) DEFAULT 'EA',
    cost_method VARCHAR(20) NOT NULL DEFAULT 'FIFO',
    gl_inventory_account_code VARCHAR(50), gl_cogs_account_code VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, sku)
);
CREATE INDEX idx_inventory_item_tenant ON fis_inventory_item(tenant_id);
CREATE TABLE fis_inventory_movement (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    item_id UUID NOT NULL, warehouse_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL, quantity BIGINT NOT NULL,
    unit_cost BIGINT, total_cost BIGINT,
    reference VARCHAR(100), reference_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_inventory_movement_item_wh ON fis_inventory_movement(item_id, warehouse_id);
CREATE TABLE fis_inventory_adjustment (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    item_id UUID NOT NULL, warehouse_id UUID NOT NULL,
    old_quantity BIGINT NOT NULL, new_quantity BIGINT NOT NULL,
    reason VARCHAR(500), unit_cost BIGINT, total_adjustment BIGINT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE fis_inventory_valuation_run (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    period VARCHAR(7) NOT NULL, run_date DATE NOT NULL,
    total_value BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
