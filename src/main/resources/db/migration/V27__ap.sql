-- V27: Accounts Payable (AP) Module
-- Creates tables for Vendor, Bill, BillLine, BillPayment, BillPaymentApplication, DebitNote

-- ============================================================================
-- Vendor
-- ============================================================================
CREATE TABLE fis_vendor (
    vendor_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    payment_terms VARCHAR(20) NOT NULL DEFAULT 'NET_30'
        CHECK (payment_terms IN ('NET_15', 'NET_30', 'NET_45', 'NET_60')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_vendor_tenant ON fis_vendor(tenant_id);
CREATE INDEX idx_vendor_code ON fis_vendor(tenant_id, code);

-- ============================================================================
-- Bill
-- ============================================================================
CREATE TABLE fis_bill (
    bill_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    vendor_id UUID NOT NULL,
    bill_number VARCHAR(50) NOT NULL,
    bill_date DATE NOT NULL,
    due_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal_amount BIGINT NOT NULL DEFAULT 0,
    tax_amount BIGINT NOT NULL DEFAULT 0,
    total_amount BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'POSTED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE')),
    description TEXT,
    reference_id VARCHAR(100),
    paid_amount BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, bill_number)
);

CREATE INDEX idx_bill_tenant ON fis_bill(tenant_id);
CREATE INDEX idx_bill_vendor ON fis_bill(tenant_id, vendor_id);
CREATE INDEX idx_bill_number ON fis_bill(tenant_id, bill_number);
CREATE INDEX idx_bill_status ON fis_bill(tenant_id, status);
CREATE INDEX idx_bill_due_date ON fis_bill(due_date);

-- ============================================================================
-- Bill Line
-- ============================================================================
CREATE TABLE fis_bill_line (
    bill_line_id UUID PRIMARY KEY,
    bill_id UUID NOT NULL REFERENCES fis_bill(bill_id),
    description VARCHAR(500) NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price BIGINT NOT NULL,
    tax_rate BIGINT NOT NULL DEFAULT 0,
    line_total BIGINT NOT NULL,
    gl_account_id UUID,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_bill_line_bill ON fis_bill_line(bill_id);
CREATE INDEX idx_bill_line_gl_account ON fis_bill_line(gl_account_id);

-- ============================================================================
-- Bill Payment
-- ============================================================================
CREATE TABLE fis_bill_payment (
    bill_payment_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    vendor_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    payment_date DATE NOT NULL,
    method VARCHAR(20) NOT NULL
        CHECK (method IN ('CASH', 'CHEQUE', 'BANK_TRANSFER', 'CARD')),
    reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPLIED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_bill_payment_tenant ON fis_bill_payment(tenant_id);
CREATE INDEX idx_bill_payment_vendor ON fis_bill_payment(tenant_id, vendor_id);
CREATE INDEX idx_bill_payment_status ON fis_bill_payment(tenant_id, status);
CREATE INDEX idx_bill_payment_date ON fis_bill_payment(payment_date);

-- ============================================================================
-- Bill Payment Application
-- ============================================================================
CREATE TABLE fis_bill_payment_application (
    application_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES fis_bill_payment(bill_payment_id),
    bill_id UUID NOT NULL REFERENCES fis_bill(bill_id),
    applied_amount BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_bill_payment_app_payment ON fis_bill_payment_application(payment_id);
CREATE INDEX idx_bill_payment_app_bill ON fis_bill_payment_application(bill_id);

-- ============================================================================
-- Debit Note
-- ============================================================================
CREATE TABLE fis_debit_note (
    debit_note_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    vendor_id UUID NOT NULL,
    original_bill_id UUID NOT NULL REFERENCES fis_bill(bill_id),
    amount BIGINT NOT NULL,
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'POSTED', 'APPLIED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_debit_note_tenant ON fis_debit_note(tenant_id);
CREATE INDEX idx_debit_note_vendor ON fis_debit_note(tenant_id, vendor_id);
CREATE INDEX idx_debit_note_bill ON fis_debit_note(original_bill_id);
CREATE INDEX idx_debit_note_status ON fis_debit_note(tenant_id, status);
