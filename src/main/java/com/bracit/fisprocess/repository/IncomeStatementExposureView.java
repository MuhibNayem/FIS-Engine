package com.bracit.fisprocess.repository;

/**
 * Aggregated income statement exposure per transaction currency for
 * functional-currency translation.
 */
public interface IncomeStatementExposureView {

    String getTransactionCurrency();

    long getSignedAmountCents();

    long getSignedBaseAmountCents();
}
