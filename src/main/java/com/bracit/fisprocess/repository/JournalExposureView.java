package com.bracit.fisprocess.repository;

/**
 * Aggregated exposure per transaction currency for period-end revaluation.
 */
public interface JournalExposureView {

    String getTransactionCurrency();

    long getSignedAmountCents();

    long getSignedBaseAmountCents();
}
