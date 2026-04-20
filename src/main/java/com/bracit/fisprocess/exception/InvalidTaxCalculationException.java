package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a tax calculation produces invalid results.
 */
public class InvalidTaxCalculationException extends FisBusinessException {

    public InvalidTaxCalculationException(UUID taxGroupId, String reason) {
        super(
                "Invalid tax calculation for group '" + taxGroupId + "': " + reason,
                HttpStatus.BAD_REQUEST,
                "/problems/invalid-tax-calculation");
    }
}
