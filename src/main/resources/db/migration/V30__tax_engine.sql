-- V30: Tax Engine Module
-- Creates tables for TaxRate, TaxGroup, TaxGroupRate, TaxJurisdiction, TaxReturn, TaxReturnLine

-- ============================================================================
-- Tax Rate
-- ============================================================================
CREATE TABLE fis_tax_rate (
    tax_rate_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    rate NUMERIC(10, 6) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    type VARCHAR(20) NOT NULL
        CHECK (type IN ('VAT', 'GST', 'SALES_TAX', 'WITHHOLDING')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_tax_rate_tenant ON fis_tax_rate(tenant_id);
CREATE INDEX idx_tax_rate_code ON fis_tax_rate(tenant_id, code);
CREATE INDEX idx_tax_rate_type ON fis_tax_rate(tenant_id, type);
CREATE INDEX idx_tax_rate_active ON fis_tax_rate(tenant_id, is_active);
CREATE INDEX idx_tax_rate_effective ON fis_tax_rate(effective_from, effective_to);

-- ============================================================================
-- Tax Group
-- ============================================================================
CREATE TABLE fis_tax_group (
    tax_group_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_tax_group_tenant ON fis_tax_group(tenant_id);

-- ============================================================================
-- Tax Group Rate (Join Table)
-- ============================================================================
CREATE TABLE fis_tax_group_rate (
    tax_group_rate_id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES fis_tax_group(tax_group_id),
    tax_rate_id UUID NOT NULL REFERENCES fis_tax_rate(tax_rate_id),
    is_compound BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_tax_group_rate_group ON fis_tax_group_rate(group_id);
CREATE INDEX idx_tax_group_rate_rate ON fis_tax_group_rate(tax_rate_id);
CREATE UNIQUE INDEX idx_tax_group_rate_unique ON fis_tax_group_rate(group_id, tax_rate_id);

-- ============================================================================
-- Tax Jurisdiction
-- ============================================================================
CREATE TABLE fis_tax_jurisdiction (
    tax_jurisdiction_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    name VARCHAR(100) NOT NULL,
    country VARCHAR(2) NOT NULL,
    region VARCHAR(100) NOT NULL,
    filing_frequency VARCHAR(20) NOT NULL
        CHECK (filing_frequency IN ('MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, name, country, region)
);

CREATE INDEX idx_tax_jurisdiction_tenant ON fis_tax_jurisdiction(tenant_id);
CREATE INDEX idx_tax_jurisdiction_country ON fis_tax_jurisdiction(country, region);

-- ============================================================================
-- Tax Return
-- ============================================================================
CREATE TABLE fis_tax_return (
    tax_return_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    jurisdiction_id UUID NOT NULL REFERENCES fis_tax_jurisdiction(tax_jurisdiction_id),
    period VARCHAR(7) NOT NULL,  -- YYYY-MM format
    filed_at TIMESTAMP WITH TIME ZONE,
    total_output_tax BIGINT NOT NULL DEFAULT 0,
    total_input_tax BIGINT NOT NULL DEFAULT 0,
    net_payable BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'FILED', 'PAID')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, jurisdiction_id, period)
);

CREATE INDEX idx_tax_return_tenant ON fis_tax_return(tenant_id);
CREATE INDEX idx_tax_return_jurisdiction ON fis_tax_return(jurisdiction_id);
CREATE INDEX idx_tax_return_period ON fis_tax_return(period);
CREATE INDEX idx_tax_return_status ON fis_tax_return(tenant_id, status);

-- ============================================================================
-- Tax Return Line
-- ============================================================================
CREATE TABLE fis_tax_return_line (
    tax_return_line_id UUID PRIMARY KEY,
    return_id UUID NOT NULL REFERENCES fis_tax_return(tax_return_id),
    tax_rate_id UUID NOT NULL REFERENCES fis_tax_rate(tax_rate_id),
    taxable_amount BIGINT NOT NULL,
    tax_amount BIGINT NOT NULL,
    direction VARCHAR(20) NOT NULL
        CHECK (direction IN ('OUTPUT', 'INPUT')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_tax_return_line_return ON fis_tax_return_line(return_id);
CREATE INDEX idx_tax_return_line_rate ON fis_tax_return_line(tax_rate_id);
CREATE INDEX idx_tax_return_line_direction ON fis_tax_return_line(direction);
