# FIS-Engine ERP Suite — Complete Implementation Plan

> **Version:** 1.0  
> **Date:** 2026-04-13  
> **Status:** Proposed  
> **Author:** Engineering Team  
> **Reviewers:** Architecture Board, Finance SME, Security Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current State Assessment](#2-current-state-assessment)
3. [Target Architecture](#3-target-architecture)
4. [Module Specifications](#4-module-specifications)
5. [Implementation Phases](#5-implementation-phases)
6. [Database Migration Plan](#6-database-migration-plan)
7. [Integration Points with GL Core](#7-integration-points-with-gl-core)
8. [Testing Strategy](#8-testing-strategy)
9. [Deployment & Rollout](#9-deployment--rollout)
10. [Risk Register](#10-risk-register)
11. [Success Criteria](#11-success-criteria)

---

## 1. Executive Summary

### 1.1 Purpose

This document defines the complete, phased implementation plan for transforming **FIS-Engine** — currently a standalone General Ledger (GL) core — into a **full-featured, enterprise-grade ERP (Enterprise Resource Planning) suite** consisting of 9 additional modules built on top of the immutable GL foundation.

### 1.2 Vision

Build a **domain-agnostic, industry-neutral financial platform** that can serve any organization — university, hospital, retailer, NGO, fintech — with a single codebase, while keeping the GL core as the immutable system of record.

### 1.3 Scope Inclusions

| Included | Reason |
|----------|--------|
| Accounts Receivable | Universal — every business invoices customers |
| Accounts Payable | Universal — every business pays vendors |
| Bank Reconciliation | Universal — every business reconciles bank statements |
| Fixed Assets | Universal — every business owns depreciating assets |
| Tax Engine | Universal — every business collects/remits tax |
| Budget Management | Universal — every organization plans spending |
| Payroll Accounting | Universal — every business pays employees |
| Inventory Valuation | Broad — any business selling physical goods |
| Multi-Entity Consolidation | Broad — any group with subsidiaries |

### 1.4 Scope Exclusions

| Excluded | Reason |
|----------|--------|
| Student billing / tuition management | Education-specific |
| Patient billing / claims processing | Healthcare-specific |
| Manufacturing cost accounting | Industry-specific |
| Hotel / restaurant management | Industry-specific |
| Construction project accounting | Industry-specific |
| CRM, HRM, document management | Outside accounting domain |

**Principle:** If a feature is needed by *every* business, it's in. If it's needed by *one* industry, it's out.

### 1.5 Guiding Principles

| Principle | Description |
|-----------|-------------|
| **GL is the Source of Truth** | Every financial transaction ultimately posts to the GL. Modules are systems of engagement; GL is the system of record. |
| **Immutability Preserved** | No module may UPDATE or DELETE posted GL entries. Corrections use reversal journals only. |
| **Multi-Tenant by Design** | Every entity, query, and operation is scoped by `tenant_id`. No cross-tenant data leakage. |
| **Event-Driven Where Possible** | Modules communicate via events (RabbitMQ + outbox), not direct coupling. |
| **Backward Compatible** | No breaking changes to existing GL APIs. New modules add new endpoints under `/v1/{module}/`. |
| **Test-Driven for Financial Code** | All services have unit tests. All GL-posting paths have integration tests. Target: 80%+ JaCoCo. |

---

## 2. Current State Assessment

### 2.1 What Exists Today

| Component | Count | Status |
|-----------|-------|--------|
| Domain entities | 18 | ✅ Production-ready |
| Service interfaces | 30 | ✅ Production-ready |
| Service implementations | 34 | ✅ Production-ready |
| REST controllers | 12 | ✅ Production-ready |
| Request DTOs | 24 | ✅ Production-ready |
| Response DTOs | 40 | ✅ Production-ready |
| Repositories | 21 | ✅ Production-ready |
| Custom exceptions | 27 | ✅ Production-ready |
| Flyway migrations | 25 (V1–V25) | ✅ Production-ready |
| Unit tests | 60 | ✅ Passing (11 pre-existing failures) |
| DLQ + alerting | ✅ | Implemented |
| API versioning | ✅ | Implemented |
| Security hardening | ✅ | Implemented |
| K8s deployment configs | ✅ | Implemented |
| Performance benchmarks | ✅ | JMH + k6 configs |

### 2.2 GL Core Capabilities (Already Built)

| Capability | Coverage |
|------------|----------|
| Double-entry journal posting | ✅ 100% |
| Append-only enforcement (DB triggers) | ✅ 100% |
| SHA-256 hash chain integrity | ✅ 100% |
| Multi-currency + FX conversion | ✅ 100% |
| Period controls (open/soft/hard close) | ✅ 100% |
| Approval workflows (maker-checker) | ✅ 100% |
| Journal reversal & correction | ✅ 100% |
| Year-end close automation | ✅ 100% |
| Period-end revaluation | ✅ 100% |
| Realized FX settlement | ✅ 100% |
| Financial reporting (10 reports) | ✅ 100% |
| Event-driven mapping engine | ✅ 100% |
| Idempotent posting (Redis + PG) | ✅ 100% |
| Outbox pattern + DLQ | ✅ 100% |
| Multi-tenant isolation | ✅ 100% |
| RBAC (Admin/Accountant/Reader) | ✅ 100% |

### 2.3 What Is Missing (The 9 Modules)

| Module | Entities | Services | Controllers | DTOs | Repos | Exceptions | Migrations | Tests | Est. Effort |
|--------|----------|----------|-------------|------|-------|------------|------------|-------|-------------|
| AR | 5 | 4 | 3 | 12 | 5 | 4 | 1 | 15 | 6 weeks |
| AP | 5 | 3 | 3 | 10 | 5 | 3 | 1 | 12 | 5 weeks |
| Bank Rec | 5 | 3 | 2 | 10 | 5 | 3 | 1 | 12 | 5 weeks |
| Fixed Assets | 4 | 3 | 2 | 10 | 4 | 3 | 1 | 12 | 5 weeks |
| Tax Engine | 6 | 2 | 1 | 10 | 6 | 3 | 1 | 10 | 4 weeks |
| Budget | 3 | 2 | 1 | 8 | 3 | 2 | 1 | 8 | 4 weeks |
| Payroll | 5 | 2 | 1 | 10 | 5 | 3 | 1 | 10 | 5 weeks |
| Inventory | 5 | 3 | 2 | 12 | 5 | 3 | 1 | 12 | 6 weeks |
| Consolidation | 4 | 2 | 1 | 8 | 4 | 2 | 1 | 8 | 6 weeks |
| **Total** | **42** | **24** | **16** | **90** | **42** | **26** | **9** | **99** | **~46 weeks** |

---

## 3. Target Architecture

### 3.1 Module Dependency Graph

```
                    ┌─────────────────────────────┐
                    │      External Systems        │
                    │  (Banks, Payment Gateways)   │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │     Event Ingestion Layer    │
                    │  (RabbitMQ + Outbox + DLQ)   │
                    └──────┬───────────┬──────────┘
                           │           │
          ┌────────────────▼──┐  ┌─────▼────────────────┐
          │   Accounts         │  │   Accounts           │
          │   Receivable (AR)  │  │   Payable (AP)       │
          └──────┬────────────┘  └──────┬───────────────┘
                 │                      │
          ┌──────▼──────────────────────▼──────────────┐
          │            Tax Engine                       │
          │   (calculates tax for AR & AP invoices)    │
          └──────┬──────────────────────┬──────────────┘
                 │                      │
    ┌────────────▼──────┐    ┌──────────▼─────────────┐
    │  Bank             │    │  Fixed Assets           │
    │  Reconciliation   │    │  (depreciation → GL)    │
    └──────┬────────────┘    └──────────┬─────────────┘
           │                            │
    ┌──────▼──────────────┐   ┌─────────▼─────────────┐
    │  Budget Management  │   │  Payroll Accounting     │
    │  (variance vs GL)   │   │  (payroll journals→GL)  │
    └──────┬──────────────┘   └─────────┬─────────────┘
           │                            │
    ┌──────▼────────────────────────────▼─────────────┐
    │           Inventory Valuation                     │
    │    (COGS posting, stock → GL adjustments)        │
    └──────────────────────┬──────────────────────────┘
                           │
              ┌────────────▼────────────┐
              │   Multi-Entity          │
              │   Consolidation         │
              │   (cross-tenant rollup) │
              └────────────┬────────────┘
                           │
              ┌────────────▼────────────┐
              │    GL CORE (existing)   │
              │  - Immutable posting    │
              │  - Hash chain           │
              │  - Period controls      │
              │  - Multi-currency       │
              │  - Financial reports    │
              └─────────────────────────┘
```

### 3.2 Data Flow: Invoice to Ledger

```
Customer places order
    ↓
AR: Invoice created (status=DRAFT)
    ↓
Tax Engine: calculates VAT 15% → adds tax lines
    ↓
AR: Invoice finalized (status=POSTED)
    ↓
GL: Journal posted
    DEBIT: Accounts Receivable     $11,500
    CREDIT: Sales Revenue          $10,000
    CREDIT: VAT Payable             $1,500
    ↓
Budget: variance checked (actual vs budget for Revenue account)
    ↓
Customer pays
    ↓
AR: Payment recorded, matched to invoice
    ↓
GL: Journal posted
    DEBIT: Bank Account            $11,500
    CREDIT: Accounts Receivable    $11,500
    ↓
Bank Rec: bank statement line auto-matched to GL entry
    ↓
Reconciliation complete
```

### 3.3 Integration Points with GL Core

| Module | Reads from GL | Writes to GL | GL Service Used |
|--------|--------------|--------------|-----------------|
| AR | Account balances, period status | Revenue, AR journals | `JournalEntryService` |
| AP | Account balances, period status | Expense, AP journals | `JournalEntryService` |
| Bank Rec | Journal entries for matching | None (read-only) | `JournalEntryRepository` |
| Fixed Assets | Depreciation expense account | Depreciation journals | `JournalEntryService` |
| Tax Engine | None | Tax liability journals | `JournalEntryService` |
| Budget | Actuals for variance | None (read-only) | `ReportingService` |
| Payroll | Expense accounts | Payroll journals | `JournalEntryService` |
| Inventory | COGS, inventory accounts | COGS, adjustment journals | `JournalEntryService` |
| Consolidation | All tenants' GL data | Elimination journals | `JournalEntryService` |

---

## 4. Module Specifications

### 4.1 Module 1: Accounts Receivable (AR)

**Purpose:** Track money owed by customers. Manage invoices, payments, credit notes, and aging.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `Customer` | `fis_customer` | id, tenantId, code, name, email, currency, creditLimit, status |
| `Invoice` | `fis_invoice` | id, tenantId, customerId, invoiceNumber, issueDate, dueDate, currency, totalAmount, taxAmount, status |
| `InvoiceLine` | `fis_invoice_line` | id, invoiceId, description, quantity, unitPrice, taxRate, lineTotal |
| `Payment` | `fis_ar_payment` | id, tenantId, customerId, amount, paymentDate, method, reference, status |
| `PaymentApplication` | `fis_payment_application` | id, paymentId, invoiceId, appliedAmount |
| `CreditNote` | `fis_credit_note` | id, tenantId, customerId, originalInvoiceId, amount, reason, status |

**Status Enums:**
- Invoice: `DRAFT` → `POSTED` → `PARTIALLY_PAID` → `PAID` → `OVERDUE` → `WRITTEN_OFF`
- Payment: `PENDING` → `APPLIED` → `CANCELLED`

**Services:**
| Service | Key Methods |
|---------|------------|
| `InvoiceService` | `create()`, `finalize()`, `void()`, `getById()`, `list()`, `getAging()` |
| `PaymentService` | `recordPayment()`, `applyToInvoices()`, `unapply()`, `list()` |
| `CreditNoteService` | `create()`, `apply()`, `list()` |
| `AgingService` | `getAgingReport(customerId?, asOfDate)` → buckets: 0-30, 31-60, 61-90, 90+ |

**GL Integration:**
- Invoice finalized → `DEBIT: Accounts Receivable / CREDIT: Revenue + Tax Payable`
- Payment applied → `DEBIT: Bank / CREDIT: Accounts Receivable`
- Credit note → reversal journal for original invoice lines
- Write-off → `DEBIT: Bad Debt Expense / CREDIT: Accounts Receivable`

**Reports:**
1. **AR Aging Report** — outstanding balances by age bucket, per customer
2. **Revenue Recognition Report** — earned vs unearned revenue by period
3. **Customer Statement** — full transaction history for a customer

**API Endpoints:**
```
POST   /v1/ar/customers
GET    /v1/ar/customers/{id}
GET    /v1/ar/customers?page=0&size=20

POST   /v1/ar/invoices
POST   /v1/ar/invoices/{id}/finalize
POST   /v1/ar/invoices/{id}/void
GET    /v1/ar/invoices/{id}
GET    /v1/ar/invoices?page=0&size=20&status=OVERDUE&customerId=...

POST   /v1/ar/payments
POST   /v1/ar/payments/{id}/apply
GET    /v1/ar/payments/{id}
GET    /v1/ar/payments?page=0&size=20

POST   /v1/ar/credit-notes
POST   /v1/ar/credit-notes/{id}/apply
GET    /v1/ar/credit-notes/{id}

GET    /v1/ar/reports/aging?asOfDate=2026-04-07
GET    /v1/ar/reports/revenue-recognition?fromDate=&toDate=
GET    /v1/ar/reports/customer-statement/{customerId}?fromDate=&toDate=
```

**Validation Rules:**
- Invoice total must equal sum of line totals
- Payment amount cannot exceed outstanding invoice balance
- Cannot void a finalized invoice that has payments applied
- Credit note amount cannot exceed original invoice amount
- Customer code must be unique per tenant

---

### 4.2 Module 2: Accounts Payable (AP)

**Purpose:** Track money owed to vendors. Manage bills, bill payments, debit notes, and AP aging.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `Vendor` | `fis_vendor` | id, tenantId, code, name, taxId, currency, paymentTerms, status |
| `Bill` | `fis_bill` | id, tenantId, vendorId, billNumber, billDate, dueDate, currency, totalAmount, taxAmount, status |
| `BillLine` | `fis_bill_line` | id, billId, description, quantity, unitPrice, taxRate, lineTotal, glAccountId |
| `BillPayment` | `fis_bill_payment` | id, tenantId, vendorId, amount, paymentDate, method, reference, status |
| `DebitNote` | `fis_debit_note` | id, tenantId, vendorId, originalBillId, amount, reason, status |

**Status Enums:**
- Bill: `DRAFT` → `POSTED` → `PARTIALLY_PAID` → `PAID` → `OVERDUE`

**Services:**
| Service | Key Methods |
|---------|------------|
| `BillService` | `create()`, `finalize()`, `void()`, `list()`, `getAging()` |
| `BillPaymentService` | `recordPayment()`, `applyToBills()`, `list()` |
| `VendorService` | `create()`, `update()`, `list()`, `getById()` |

**GL Integration:**
- Bill finalized → `DEBIT: Expense / CREDIT: Accounts Payable + Tax Receivable`
- Payment made → `DEBIT: Accounts Payable / CREDIT: Bank`
- Debit note → reversal journal for original bill lines

**Reports:**
1. **AP Aging Report** — outstanding bills by age bucket, per vendor
2. **Early Payment Discount Report** — discounts captured by paying early
3. **Vendor Statement** — full transaction history for a vendor

---

### 4.3 Module 3: Bank Reconciliation

**Purpose:** Match GL journal entries to bank statement lines. Identify discrepancies. Lock reconciled periods.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `BankAccount` | `fis_bank_account` | id, tenantId, accountNumber, bankName, currency, glAccountId, status |
| `BankStatement` | `fis_bank_statement` | id, tenantId, bankAccountId, statementDate, openingBalance, closingBalance, status |
| `BankStatementLine` | `fis_bank_statement_line` | id, statementId, date, description, amount, reference, matched, matchedJournalLineId |
| `Reconciliation` | `fis_reconciliation` | id, tenantId, bankAccountId, startDate, endDate, reconciledAt, reconciledBy, status, discrepancy |
| `ReconciliationMatch` | `fis_reconciliation_match` | id, reconciliationId, statementLineId, journalLineId, amount |

**Matching Algorithm:**
1. **Exact match:** Same amount + same date (±1 day) + same reference
2. **Fuzzy match:** Same amount + date within ±7 days
3. **Split match:** One statement line matches multiple journal lines summing to same amount
4. **Aggregate match:** Multiple statement lines sum to one journal line amount

**Services:**
| Service | Key Methods |
|---------|------------|
| `BankAccountService` | `register()`, `list()`, `getById()` |
| `BankStatementService` | `importCsv()`, `importOfx()`, `list()`, `getById()` |
| `ReconciliationService` | `start()`, `autoMatch()`, `manualMatch()`, `unmatch()`, `complete()`, `list()` |

**GL Integration:** Read-only. Reads journal entries for matching. Does NOT post to GL directly. May suggest adjustment journals.

**Reports:**
1. **Reconciliation Summary** — matched, unmatched, outstanding items per period
2. **Outstanding Items Report** — items not yet reconciled, sorted by age

---

### 4.4 Module 4: Fixed Assets

**Purpose:** Register, depreciate, and dispose of long-term assets. Auto-post depreciation journals to GL.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `AssetCategory` | `fis_asset_category` | id, tenantId, name, defaultUsefulLifeMonths, defaultDepreciationMethod, glAccountId |
| `FixedAsset` | `fis_fixed_asset` | id, tenantId, categoryId, assetTag, name, acquisitionDate, acquisitionCost, salvageValue, usefulLifeMonths, depreciationMethod, accumulatedDepreciation, netBookValue, location, status |
| `AssetDepreciationRun` | `fis_asset_depreciation_run` | id, tenantId, period, runDate, totalDepreciation, status, createdBy |
| `AssetDepreciationLine` | `fis_depreciation_line` | id, runId, assetId, monthlyDepreciation, accumulatedDepreciation, netBookValue |
| `AssetDisposal` | `fis_asset_disposal` | id, tenantId, assetId, disposalDate, saleProceeds, netBookValue, gainLoss, disposalType |

**Depreciation Methods:**
| Method | Formula |
|--------|---------|
| **Straight-Line** | `(Cost - Salvage) / Useful Life` per month |
| **Declining Balance** | `Net Book Value × (2 / Useful Life)` per month |
| **Sum-of-Years-Digits** | `(Cost - Salvage) × (Remaining Life / SYD)` per month |
| **Units of Production** | `(Cost - Salvage) × (Units This Period / Total Expected Units)` |

**Services:**
| Service | Key Methods |
|---------|------------|
| `FixedAssetService` | `register()`, `transfer()`, `revalue()`, `list()`, `getById()` |
| `DepreciationService` | `runMonthly(period)`, `calculate()`, `postToGL()`, `getSchedule()` |
| `AssetDisposalService` | `dispose()`, `calculateGainLoss()`, `postToGL()` |

**GL Integration:**
- Monthly depreciation → `DEBIT: Depreciation Expense / CREDIT: Accumulated Depreciation`
- Asset disposal → `DEBIT: Bank (proceeds) + Accumulated Depreciation / CREDIT: Fixed Asset + Gain/Loss`

**Reports:**
1. **Asset Register** — all assets with cost, accumulated depreciation, NBV
2. **Depreciation Schedule** — monthly depreciation by asset and category
3. **Disposal Report** — assets disposed with gain/loss analysis

---

### 4.5 Module 5: Tax Engine

**Purpose:** Calculate, track, and report taxes on all transactions. Support multiple tax types and jurisdictions.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `TaxRate` | `fis_tax_rate` | id, tenantId, code, name, rate (percentage), effectiveFrom, effectiveTo, type (VAT/GST/WH/SALES), isActive |
| `TaxGroup` | `fis_tax_group` | id, tenantId, name, description |
| `TaxGroupRate` | `fis_tax_group_rate` | id, groupId, taxRateId, isCompound |
| `TaxJurisdiction` | `fis_tax_jurisdiction` | id, tenantId, name, country, region, filingFrequency |
| `TaxReturn` | `fis_tax_return` | id, tenantId, jurisdictionId, period, filedAt, totalOutputTax, totalInputTax, netPayable, status |
| `TaxReturnLine` | `fis_tax_return_line` | id, returnId, taxRateId, taxableAmount, taxAmount, direction (OUTPUT/INPUT) |

**Tax Calculation:**
```
Tax-exclusive pricing:  Tax = Net Amount × Rate
Tax-inclusive pricing:   Tax = Gross Amount × Rate / (1 + Rate)
Compound tax:            Tax = Base × Rate1, then Tax2 = (Base + Tax1) × Rate2
```

**Services:**
| Service | Key Methods |
|---------|------------|
| `TaxService` | `calculate(amount, taxGroupId, isInclusive)`, `getEffectiveRate(taxGroupId)` |
| `TaxReturnService` | `generate(period, jurisdictionId)`, `file()`, `list()`, `getById()` |

**GL Integration:**
- Tax on sales → part of invoice journal (credit to Tax Payable account)
- Tax on purchases → part of bill journal (debit to Tax Receivable account)
- Tax return filed → `DEBIT: Tax Payable / CREDIT: Bank` (if paying) or vice versa (if refund)

**Reports:**
1. **Tax Return Summary** — output tax vs input tax, net payable/receivable
2. **Tax Liability by Jurisdiction** — outstanding tax obligations per jurisdiction

---

### 4.6 Module 6: Budget Management

**Purpose:** Plan and track spending against budgets. Alert on overspending. Analyze variances.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `Budget` | `fis_budget` | id, tenantId, name, fiscalYear, status (DRAFT/APPROVED/REVISED), createdBy |
| `BudgetLine` | `fis_budget_line` | id, budgetId, accountId, department, month, budgetedAmount, currency |
| `BudgetTransfer` | `fis_budget_transfer` | id, tenantId, budgetId, fromAccountId, toAccountId, amount, approvedBy, reason |

**Services:**
| Service | Key Methods |
|---------|------------|
| `BudgetService` | `create()`, `approve()`, `revise()`, `list()`, `getById()` |
| `BudgetVarianceService` | `getVariance(budgetId, accountId, period)`, `getUtilization(budgetId)`, `checkThreshold(accountId, amount)` |

**GL Integration:** Read-only. Reads actual GL postings per account per period. Compares to budgeted amounts.

**Variance Calculation:**
```
Variance = Budgeted Amount - Actual Amount
Variance % = (Variance / Budgeted Amount) × 100
```

**Thresholds:**
- 0–80%: ✅ On track
- 80–100%: ⚠️ Warning
- >100%: 🔴 Over budget (optionally block posting)

**Reports:**
1. **Budget vs Actual** — line-by-line comparison with variance and variance %
2. **Budget Utilization** — percentage spent per account, ranked by highest utilization

---

### 4.7 Module 7: Payroll Accounting

**Purpose:** Process employee payroll. Calculate gross-to-net. Post payroll journals to GL.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `Employee` | `fis_employee` | id, tenantId, code, name, department, glDepartmentAccountId, status |
| `PayrollRun` | `fis_payroll_run` | id, tenantId, period, runDate, totalGross, totalDeductions, totalNet, status, approvedBy |
| `PayrollLine` | `fis_payroll_line` | id, runId, employeeId, grossSalary, allowances, deductions, taxableIncome, incomeTax, socialSecurity, netPay |
| `PayrollTax` | `fis_payroll_tax` | id, tenantId, taxName, rate, threshold, glAccountId |
| `PayrollDeduction` | `fis_payroll_deduction` | id, tenantId, name, type (TAX/LOAN/INSURANCE), glAccountId, isMandatory |

**Gross-to-Net Calculation:**
```
Gross Salary = Basic + Allowances
Taxable Income = Gross - Tax-exempt allowances
Income Tax = apply progressive tax brackets
Social Security = Gross × SS rate (capped)
Net Pay = Gross - Income Tax - SS - Other deductions
```

**Services:**
| Service | Key Methods |
|---------|------------|
| `PayrollService` | `createRun()`, `calculateAll()`, `approve()`, `postToGL()`, `list()`, `getById()` |

**GL Integration:**
- Payroll posted → Multi-line journal:
  - `DEBIT: Salary Expense` (gross)
  - `DEBIT: Benefits Expense` (employer contributions)
  - `CREDIT: Income Tax Payable`
  - `CREDIT: Social Security Payable`
  - `CREDIT: Salary Payable` (net)

**Reports:**
1. **Payroll Register** — all employees with gross, deductions, net
2. **Department-wise Payroll Summary** — totals by department

---

### 4.8 Module 8: Inventory Valuation

**Purpose:** Track stock across warehouses. Value inventory. Calculate COGS. Post valuation adjustments to GL.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `Warehouse` | `fis_warehouse` | id, tenantId, code, name, location, glAccountId |
| `InventoryItem` | `fis_inventory_item` | id, tenantId, sku, name, category, uom, costMethod, glInventoryAccountId, glCOGSAccountId |
| `InventoryMovement` | `fis_inventory_movement` | id, tenantId, itemId, warehouseId, type (RECEIPT/ISSUE/TRANSFER/ADJUSTMENT), quantity, unitCost, totalCost, referenceDate |
| `InventoryAdjustment` | `fis_inventory_adjustment` | id, tenantId, itemId, warehouseId, oldQty, newQty, reason, unitCost, totalAdjustment |
| `InventoryValuationRun` | `fis_inventory_valuation_run` | id, tenantId, period, runDate, totalValue, status |

**Valuation Methods:**
| Method | COGS Formula |
|--------|-------------|
| **FIFO** | Cost of oldest inventory issued first |
| **Weighted Average** | Total Cost / Total Quantity = avg cost per unit |

**Services:**
| Service | Key Methods |
|---------|------------|
| `InventoryService` | `receipt()`, `issue()`, `transfer()`, `adjust()`, `getStockLevel()`, `list()` |
| `ValuationService` | `calculateCOGS()`, `runValuation(period)`, `postToGL()`, `getValuationReport()` |

**GL Integration:**
- Goods received → `DEBIT: Inventory / CREDIT: GRN Clearing`
- Goods issued (sale) → `DEBIT: COGS / CREDIT: Inventory`
- Inventory adjustment → `DEBIT/CREDIT: Inventory / CREDIT/DEBIT: Adjustment Account`

**Reports:**
1. **Stock Valuation Report** — quantity × unit cost = total value per item/warehouse
2. **Inventory Aging Report** — stock by age (0-30, 31-90, 90-180, 180+ days)
3. **COGS Report** — cost of goods sold by item and period

---

### 4.9 Module 9: Multi-Entity Consolidation

**Purpose:** Combine financial data from multiple tenants/entities. Apply inter-company eliminations. Produce group-level reports.

**Entities:**

| Entity | Table | Key Fields |
|--------|-------|------------|
| `ConsolidationGroup` | `fis_consolidation_group` | id, parentTenantId, name, description, baseCurrency |
| `ConsolidationMember` | `fis_consolidation_member` | id, groupId, tenantId, ownershipPercentage, currency, translationMethod (CLOSING/AVERAGE) |
| `ConsolidationRun` | `fis_consolidation_run` | id, groupId, period, runDate, status, totalAssets, totalLiabilities, totalEquity, netIncome |
| `EliminationRule` | `fis_elimination_rule` | id, groupId, fromAccountCode, toAccountCode, description, isActive |
| `EliminationEntry` | `fis_elimination_entry` | id, runId, fromTenantId, toTenantId, accountCode, amount, description |

**Consolidation Process:**
1. Fetch trial balance from each member entity
2. Translate foreign subsidiaries to group base currency
3. Apply elimination rules (inter-company receivables/payables, sales/purchases)
4. Calculate minority interest (non-controlling ownership %)
5. Produce consolidated reports

**Services:**
| Service | Key Methods |
|---------|------------|
| `ConsolidationService` | `createGroup()`, `addMember()`, `run(period)`, `applyEliminations()`, `getConsolidatedReports()` |

**GL Integration:**
- Reads trial balance from all member tenants
- Posts elimination journals to each member's GL
- Produces consolidated reports without modifying individual tenant data

**Reports:**
1. **Consolidated Trial Balance** — combined TB for all entities after eliminations
2. **Consolidated Balance Sheet** — group-level assets, liabilities, equity
3. **Elimination Report** — all inter-company eliminations applied

---

## 5. Implementation Phases

### Phase 1: Foundation & Accounts Receivable (Weeks 1–6)

**Objective:** Establish module patterns, build AR as the template for all subsequent modules.

**Deliverables:**
- [ ] V26 Flyway migration (AR tables)
- [ ] 5 AR entities with proper JPA relationships
- [ ] 4 AR services (interface + impl)
- [ ] 3 AR REST controllers
- [ ] 12 AR DTOs (request + response)
- [ ] 5 AR repositories with custom queries
- [ ] 4 AR-specific exceptions
- [ ] 15 unit tests (services) + 5 integration tests (controllers)
- [ ] GL integration: invoice finalization posts journal, payment application posts journal
- [ ] API documentation in OpenAPI spec

**Acceptance Criteria:**
- ✅ Can create a customer
- ✅ Can create and finalize an invoice (auto-posts to GL)
- ✅ Can record and apply a partial or full payment
- ✅ Can issue a credit note and apply it
- ✅ Can generate AR aging report
- ✅ All GL journals are balanced and immutable
- ✅ Tests pass, JaCoCo > 80% for AR services

**Risk:** AR is the most complex module (payments, applications, aging). If this phase succeeds, all others follow the same pattern.

---

### Phase 2: Accounts Payable + Tax Engine (Weeks 7–14)

**Objective:** Build AP (mirrors AR for vendor side) and Tax Engine (shared by AR and AP).

**Deliverables:**
- [ ] V27 Flyway migration (AP tables)
- [ ] 5 AP entities, 3 services, 3 controllers, 10 DTOs, 5 repos, 3 exceptions
- [ ] V30 Flyway migration (Tax tables)
- [ ] 6 Tax entities, 2 services, 1 controller, 10 DTOs, 6 repos, 3 exceptions
- [ ] GL integration for AP bills and payments
- [ ] Tax calculation integrated into AR invoices and AP bills
- [ ] 22 unit tests + 10 integration tests

**Acceptance Criteria:**
- ✅ Can create a vendor and record a bill (auto-posts to GL with tax)
- ✅ Can record bill payment
- ✅ Can generate AP aging report
- ✅ Tax is auto-calculated on AR invoices and AP bills
- ✅ Tax return can be generated for a period
- ✅ All cross-module tests pass (AR+Tax, AP+Tax)

---

### Phase 3: Bank Reconciliation (Weeks 15–19)

**Objective:** Match bank statements to GL entries. Provide reconciliation workflow.

**Deliverables:**
- [ ] V28 Flyway migration (Bank Rec tables)
- [ ] 5 Bank Rec entities, 3 services, 2 controllers, 10 DTOs, 5 repos, 3 exceptions
- [ ] CSV/OFX statement import
- [ ] Auto-matching algorithm (exact + fuzzy)
- [ ] Manual matching UI workflow
- [ ] Reconciliation lock (protects reconciled period)
- [ ] 12 unit tests + 5 integration tests

**Acceptance Criteria:**
- ✅ Can register a bank account linked to a GL account
- ✅ Can import a bank statement (CSV or OFX)
- ✅ Auto-matching works for 80%+ of lines
- ✅ Can manually match remaining lines
- ✅ Can complete reconciliation (lock period)
- ✅ Can view outstanding/unmatched items

---

### Phase 4: Fixed Assets (Weeks 20–24)

**Objective:** Asset register with automated depreciation posting to GL.

**Deliverables:**
- [ ] V29 Flyway migration (Fixed Asset tables)
- [ ] 4 Asset entities, 3 services, 2 controllers, 10 DTOs, 4 repos, 3 exceptions
- [ ] 4 depreciation methods implemented
- [ ] Monthly depreciation run (batch posts to GL)
- [ ] Asset disposal with gain/loss calculation
- [ ] 12 unit tests + 5 integration tests

**Acceptance Criteria:**
- ✅ Can register an asset with acquisition details
- ✅ Can run monthly depreciation (auto-posts journals to GL)
- ✅ Can dispose an asset (sale, scrap) with gain/loss journal
- ✅ Asset register report shows NBV for all assets
- ✅ Depreciation schedule shows monthly breakdown

---

### Phase 5: Budget Management (Weeks 25–28)

**Objective:** Budget creation, approval, and variance analysis against GL actuals.

**Deliverables:**
- [ ] V31 Flyway migration (Budget tables)
- [ ] 3 Budget entities, 2 services, 1 controller, 8 DTOs, 3 repos, 2 exceptions
- [ ] Budget creation and approval workflow
- [ ] Budget vs Actual variance reports
- [ ] Threshold alerts (80% warning, 100% block)
- [ ] Budget transfer with approval
- [ ] 8 unit tests + 4 integration tests

**Acceptance Criteria:**
- ✅ Can create and approve an annual budget by account and month
- ✅ Can view budget vs actual variance report
- ✅ Can set alerts at 80% and 100% utilization
- ✅ Budget transfers require approval
- ✅ All reads from GL are read-only (no writes)

---

### Phase 6: Payroll Accounting (Weeks 29–33)

**Objective:** Process payroll runs and post journals to GL.

**Deliverables:**
- [ ] V32 Flyway migration (Payroll tables)
- [ ] 5 Payroll entities, 2 services, 1 controller, 10 DTOs, 5 repos, 3 exceptions
- [ ] Gross-to-net calculation engine
- [ ] Statutory deduction support (tax, SS, provident fund)
- [ ] Payroll run approval and GL posting
- [ ] 10 unit tests + 5 integration tests

**Acceptance Criteria:**
- ✅ Can register employees with salary details
- ✅ Can run payroll calculation (gross-to-net)
- ✅ Can approve payroll and post multi-line journals to GL
- ✅ Payroll register shows all employees with net pay
- ✅ Department-wise summary available

---

### Phase 7: Inventory Valuation (Weeks 34–39)

**Objective:** Track stock, calculate COGS, post valuation adjustments to GL.

**Deliverables:**
- [ ] V33 Flyway migration (Inventory tables)
- [ ] 5 Inventory entities, 3 services, 2 controllers, 12 DTOs, 5 repos, 3 exceptions
- [ ] FIFO and Weighted Average valuation methods
- [ ] COGS calculation on inventory issue
- [ ] Multi-warehouse support
- [ ] Inventory adjustment with GL posting
- [ ] 12 unit tests + 5 integration tests

**Acceptance Criteria:**
- ✅ Can register warehouses and inventory items
- ✅ Can record receipts, issues, and transfers
- ✅ FIFO and Weighted Average both produce correct COGS
- ✅ Inventory valuation report shows total stock value
- ✅ Adjustments post correcting journals to GL

---

### Phase 8: Multi-Entity Consolidation (Weeks 40–45)

**Objective:** Roll up multiple tenants into consolidated reports with eliminations.

**Deliverables:**
- [ ] V34 Flyway migration (Consolidation tables)
- [ ] 4 Consolidation entities, 2 services, 1 controller, 8 DTOs, 4 repos, 2 exceptions
- [ ] Cross-tenant data aggregation
- Currency translation for foreign subsidiaries
- [ ] Inter-company elimination rules
- [ ] Consolidated TB, Balance Sheet, P&L
- [ ] 8 unit tests + 5 integration tests

**Acceptance Criteria:**
- ✅ Can create a consolidation group with member entities
- ✅ Can run consolidation for a period
- ✅ Elimination rules remove inter-company balances
- ✅ Consolidated reports are mathematically correct
- ✅ Minority interest is calculated correctly

---

### Phase 9: Integration Testing & Hardening (Weeks 46–50)

**Objective:** End-to-end testing of all modules together. Performance tuning. Security audit.

**Deliverables:**
- [ ] Full integration test suite (all 9 modules + GL)
- [ ] Performance benchmarks for all critical paths
- [ ] Security vulnerability scan (OWASP, Snyk)
- [ ] API documentation complete for all endpoints
- [ ] User guide / runbook for all modules
- [ ] Load testing (k6) at 1000 TPS
- [ ] JaCoCo coverage ≥ 80% overall

**Acceptance Criteria:**
- ✅ All 300+ tests pass
- ✅ p95 latency < 100ms for all endpoints
- ✅ Zero critical/high security vulnerabilities
- ✅ Complete OpenAPI spec for all modules
- ✅ Documentation complete

---

### Phase 10: Production Deployment & Pilot (Weeks 51–54)

**Objective:** Deploy to staging. Run pilot with test data. Validate. Go live.

**Deliverables:**
- [ ] Staging environment with full data set
- [ ] Pilot run with 10K synthetic transactions
- [ ] Incident response runbook
- [ ] Production deployment
- [ ] Post-launch monitoring dashboards

**Acceptance Criteria:**
- ✅ Pilot processes 10K transactions with zero errors
- ✅ All dashboards operational
- ✅ On-call procedures documented
- ✅ Production deployment successful

---

## 6. Database Migration Plan

### Migration Schedule

| Migration | Module | Tables Created | Est. Rows (Year 1, 50K users) |
|-----------|--------|---------------|-------------------------------|
| V26 | AR | 6 tables | Invoices: 500K, Payments: 400K |
| V27 | AP | 5 tables | Bills: 200K, BillPayments: 180K |
| V28 | Bank Rec | 5 tables | StatementLines: 1M, Matches: 800K |
| V29 | Fixed Assets | 5 tables | Assets: 50K, DepreciationLines: 600K |
| V30 | Tax Engine | 6 tables | TaxRates: 100, TaxReturns: 500 |
| V31 | Budget | 3 tables | BudgetLines: 100K |
| V32 | Payroll | 5 tables | PayrollLines: 600K |
| V33 | Inventory | 5 tables | Movements: 2M, Adjustments: 50K |
| V34 | Consolidation | 4 tables | Runs: 200, Eliminations: 10K |

### Migration Guidelines
- All migrations use `IF NOT EXISTS` guards
- FK constraints with `ON DELETE RESTRICT` for data integrity
- Indexes on all FK columns and frequently queried columns
- Composite indexes for common query patterns (tenant_id + status + date)
- Forward-only — no down migrations
- Tested against PostgreSQL 16+

---

## 7. Integration Points with GL Core

### Journal Posting Convention

Every module that posts to GL must follow this convention:

```
eventId format: {MODULE}-{ENTITY_ID}-{SEQUENCE}
Example: AR-INV-000123-001

description format: "{Module}: {Entity Description}"
Example: "AR: Invoice INV-000123 for Customer ABC"

referenceId format: "{MODULE_REF}"
Example: "INV-000123"
```

### Account Mapping

Each module must define default GL accounts:
| Module | Default GL Account | Purpose |
|--------|-------------------|---------|
| AR | Accounts Receivable (Asset) | Customer balances |
| AP | Accounts Payable (Liability) | Vendor balances |
| Bank Rec | Bank Account (Asset) | Cash balances |
| Fixed Assets | Fixed Assets (Asset) + Accumulated Depreciation (Contra Asset) |
| Tax | Tax Payable (Liability) + Tax Receivable (Asset) |
| Payroll | Salary Expense (Expense) + various payable accounts |
| Inventory | Inventory (Asset) + COGS (Expense) |

### Period Control

All modules must respect GL period status:
- Cannot finalize invoices for closed periods
- Cannot run payroll for closed periods
- Cannot record depreciation for closed periods
- Bank reconciliation locks the period once completed

---

## 8. Testing Strategy

### 8.1 Unit Tests
- Every service method tested in isolation
- Mock all dependencies (repositories, GL services)
- Test all validation paths (null checks, business rules)
- Test all exception-throwing paths
- Target: 80%+ JaCoCo line coverage per module

### 8.2 Integration Tests
- Controller endpoints tested with MockMvc
- Full request → service → repository → GL posting → response flow
- Test with Testcontainers (PostgreSQL)
- Test multi-tenant isolation
- Test period controls

### 8.3 End-to-End Tests
- AR: Create customer → create invoice → finalize → receive payment → reconcile
- AP: Create vendor → create bill → finalize → pay bill → reconcile
- Full cycle: Invoice → Payment → Bank Rec → Period Close → Reports

### 8.4 Performance Tests
- JMH benchmarks for critical paths (tax calculation, depreciation, COGS)
- k6 load tests for all endpoints
- Target: p95 < 100ms, p99 < 200ms at 1000 TPS

---

## 9. Deployment & Rollout

### 9.1 Deployment Strategy
- Blue-green deployment via Kubernetes
- Database migrations run before application start
- Feature flags for each module (can enable/disable per tenant)
- Rollback: migrate down not supported; application rollback only

### 9.2 Rollout Plan
| Stage | Audience | Duration | Success Criteria |
|-------|----------|----------|-----------------|
| Alpha | Internal engineering | 2 weeks | All tests pass, no critical bugs |
| Beta | Selected pilot customers | 4 weeks | < 5 bugs reported, performance stable |
| GA | All tenants | Ongoing | SLA met, no P1 incidents |

---

## 10. Risk Register

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Scope creep (feature bloat) | High | High | Strict scope control. Industry-specific features deferred to v2 |
| GL posting bugs corrupt ledger | Medium | Critical | Comprehensive integration tests. Immutable DB triggers as safety net |
| Performance degradation at scale | Medium | High | JMH benchmarks early. Database indexing strategy. Read replicas for reports |
| Tax calculation errors | Medium | High | Unit tests for every tax scenario. Independent audit of tax engine |
| Data migration from legacy systems | High | Medium | Provide import tools. Manual data validation scripts |
| Regulatory non-compliance | Low | Critical | Engage accounting firm for review before GA |
| Team capacity / timeline | High | Medium | Modular phases. Each phase delivers independently. Can stop at any phase |

---

## 11. Success Criteria

### 11.1 Technical
- [ ] All 9 modules compile and pass tests
- [ ] JaCoCo coverage ≥ 80% overall
- [ ] p95 latency < 100ms for all API endpoints
- [ ] Zero critical/high security vulnerabilities
- [ ] Complete OpenAPI documentation

### 11.2 Functional
- [ ] End-to-end AR cycle works (invoice → payment → aging)
- [ ] End-to-end AP cycle works (bill → payment → aging)
- [ ] Bank reconciliation auto-matches > 80% of lines
- [ ] Depreciation posts correct journals to GL
- [ ] Tax calculation is accurate for all supported types
- [ ] Budget variance reports match manual calculations
- [ ] Payroll gross-to-net matches statutory rules
- [ ] Inventory COGS matches FIFO/WA manual calculations
- [ ] Consolidated reports match sum of individual entity reports (after eliminations)

### 11.3 Business
- [ ] Pilot customer successfully processes 1 month of real transactions
- [ ] Month-end close time reduced from days to < 1 hour
- [ ] Zero audit findings related to data integrity
- [ ] System handles 50K users concurrently without degradation

---

## Appendix A: File Structure (Post-Implementation)

```
src/main/java/com/bracit/fisprocess/
├── domain/
│   ├── entity/          # 18 existing + 42 new = 60 entities
│   └── enums/           # 11 existing + ~25 new = ~36 enums
├── dto/
│   ├── request/         # 24 existing + ~90 new = ~114 DTOs
│   └── response/        # 40 existing + ~90 new = ~130 DTOs
├── repository/          # 21 existing + ~42 new = ~63 repositories
├── service/             # 30 existing + ~24 new = ~54 interfaces
│   └── impl/            # 34 existing + ~24 new = ~58 implementations
├── controller/          # 12 existing + ~16 new = ~28 controllers
├── exception/           # 27 existing + ~26 new = ~53 exceptions
├── config/              # Existing configs + module configs
├── scheduling/          # Existing jobs + depreciation/payroll jobs
└── FisProcessApplication.java

src/main/resources/db/migration/
├── V1__ through V25__  # Existing
├── V26__ar.sql
├── V27__ap.sql
├── V28__bank_reconciliation.sql
├── V29__fixed_assets.sql
├── V30__tax_engine.sql
├── V31__budget.sql
├── V32__payroll.sql
├── V33__inventory.sql
└── V34__consolidation.sql
```

## Appendix B: Estimated Totals

| Artifact | Count |
|----------|-------|
| New entities | 42 |
| New enums | ~25 |
| New services | 24 interfaces + 24 implementations |
| New controllers | 16 |
| New DTOs | ~180 |
| New repositories | 42 |
| New exceptions | 26 |
| New Flyway migrations | 9 |
| New unit tests | ~200 |
| New integration tests | ~100 |
| **Total new lines of code** | **~80,000–100,000** |
| **Estimated timeline** | **50–54 weeks** |
| **Estimated team size** | **4–6 engineers** |

---

*End of Document*
