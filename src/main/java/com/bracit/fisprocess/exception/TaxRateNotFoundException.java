package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a tax rate with the requested ID cannot be found.
 */
public class TaxRateNotFoundException extends FisBusinessException {

    public TaxRateNotFoundException(UUID taxRateId) {
        super(
                "Tax rate with ID '" + taxRateId + "' not found.",
                HttpStatus.NOT_FOUND,
                "/problems/tax-rate-not-found");
    }
}
