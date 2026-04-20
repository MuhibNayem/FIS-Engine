CREATE TABLE fis_employee (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL, name VARCHAR(200) NOT NULL,
    department VARCHAR(100), gl_department_account_code VARCHAR(50),
    basic_salary BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_employee_tenant ON fis_employee(tenant_id);
CREATE TABLE fis_payroll_run (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    period VARCHAR(7) NOT NULL, run_date DATE NOT NULL,
    total_gross BIGINT NOT NULL, total_deductions BIGINT NOT NULL,
    total_net BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approved_by VARCHAR(100), created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE fis_payroll_line (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    run_id UUID NOT NULL REFERENCES fis_payroll_run(id),
    employee_id UUID NOT NULL, gross_salary BIGINT NOT NULL,
    allowances BIGINT DEFAULT 0, deductions BIGINT DEFAULT 0,
    taxable_income BIGINT NOT NULL, income_tax BIGINT DEFAULT 0,
    social_security BIGINT DEFAULT 0, net_pay BIGINT NOT NULL
);
CREATE TABLE fis_payroll_tax (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    tax_name VARCHAR(100) NOT NULL, rate NUMERIC(10,4),
    threshold BIGINT, gl_account_code VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE fis_payroll_deduction (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL, type VARCHAR(30) NOT NULL,
    gl_account_code VARCHAR(50), is_mandatory BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
