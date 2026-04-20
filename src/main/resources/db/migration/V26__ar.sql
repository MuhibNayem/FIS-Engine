-- V26: Accounts Receivable (AR) Module
-- Creates tables for Customer, Invoice, InvoiceLine, ARPayment, PaymentApplication, CreditNote

-- ============================================================================
-- Customer
-- ============================================================================
CREATE TABLE fis_customer (
    customer_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    credit_limit BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_customer_tenant ON fis_customer(tenant_id);
CREATE INDEX idx_customer_code ON fis_customer(tenant_id, code);

-- ============================================================================
-- Invoice
-- ============================================================================
CREATE TABLE fis_invoice (
    invoice_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    customer_id UUID NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal_amount BIGINT NOT NULL DEFAULT 0,
    tax_amount BIGINT NOT NULL DEFAULT 0,
    total_amount BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'POSTED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'WRITTEN_OFF')),
    description TEXT,
    reference_id VARCHAR(100),
    paid_amount BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX idx_invoice_tenant ON fis_invoice(tenant_id);
CREATE INDEX idx_invoice_customer ON fis_invoice(tenant_id, customer_id);
CREATE INDEX idx_invoice_number ON fis_invoice(tenant_id, invoice_number);
CREATE INDEX idx_invoice_status ON fis_invoice(tenant_id, status);
CREATE INDEX idx_invoice_due_date ON fis_invoice(due_date);

-- ============================================================================
-- Invoice Line
-- ============================================================================
CREATE TABLE fis_invoice_line (
    invoice_line_id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES fis_invoice(invoice_id),
    description VARCHAR(500) NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price BIGINT NOT NULL,
    tax_rate BIGINT NOT NULL DEFAULT 0,
    line_total BIGINT NOT NULL,
    gl_account_id UUID,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_invoice_line_invoice ON fis_invoice_line(invoice_id);
CREATE INDEX idx_invoice_line_gl_account ON fis_invoice_line(gl_account_id);

-- ============================================================================
-- AR Payment
-- ============================================================================
CREATE TABLE fis_ar_payment (
    payment_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    customer_id UUID NOT NULL,
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

CREATE INDEX idx_payment_tenant ON fis_ar_payment(tenant_id);
CREATE INDEX idx_payment_customer ON fis_ar_payment(tenant_id, customer_id);
CREATE INDEX idx_payment_status ON fis_ar_payment(tenant_id, status);
CREATE INDEX idx_payment_date ON fis_ar_payment(payment_date);

-- ============================================================================
-- Payment Application
-- ============================================================================
CREATE TABLE fis_payment_application (
    application_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES fis_ar_payment(payment_id),
    invoice_id UUID NOT NULL REFERENCES fis_invoice(invoice_id),
    applied_amount BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payment_app_payment ON fis_payment_application(payment_id);
CREATE INDEX idx_payment_app_invoice ON fis_payment_application(invoice_id);

-- ============================================================================
-- Credit Note
-- ============================================================================
CREATE TABLE fis_credit_note (
    credit_note_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES fis_business_entity(tenant_id),
    customer_id UUID NOT NULL,
    original_invoice_id UUID NOT NULL REFERENCES fis_invoice(invoice_id),
    amount BIGINT NOT NULL,
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'POSTED', 'APPLIED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_credit_note_tenant ON fis_credit_note(tenant_id);
CREATE INDEX idx_credit_note_customer ON fis_credit_note(tenant_id, customer_id);
CREATE INDEX idx_credit_note_invoice ON fis_credit_note(original_invoice_id);
CREATE INDEX idx_credit_note_status ON fis_credit_note(tenant_id, status);
