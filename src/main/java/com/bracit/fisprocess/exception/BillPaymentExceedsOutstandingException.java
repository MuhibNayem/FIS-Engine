package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a payment amount exceeds the outstanding balance of the target bill(s).
 */
public class BillPaymentExceedsOutstandingException extends FisBusinessException {

    public BillPaymentExceedsOutstandingException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "/problems/bill-payment-exceeds-outstanding");
    }
}
