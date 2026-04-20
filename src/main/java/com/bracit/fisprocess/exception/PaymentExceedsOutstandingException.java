package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a payment amount exceeds the outstanding balance of the target invoice(s).
 */
public class PaymentExceedsOutstandingException extends FisBusinessException {

    public PaymentExceedsOutstandingException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "/problems/payment-exceeds-outstanding");
    }
}
