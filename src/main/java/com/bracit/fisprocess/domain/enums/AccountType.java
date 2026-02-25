package com.bracit.fisprocess.domain.enums;

/**
 * Standard account types used in double-entry accounting.
 * <p>
 * The fundamental accounting equation enforced by the FIS Engine is:
 * {@code Assets = Liabilities + Equity + Revenue - Expenses}
 */
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}
