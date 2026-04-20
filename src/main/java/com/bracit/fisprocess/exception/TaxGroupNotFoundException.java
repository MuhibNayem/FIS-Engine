package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a tax group with the requested ID cannot be found.
 */
public class TaxGroupNotFoundException extends FisBusinessException {

    public TaxGroupNotFoundException(UUID taxGroupId) {
        super(
                "Tax group with ID '" + taxGroupId + "' not found.",
                HttpStatus.NOT_FOUND,
                "/problems/tax-group-not-found");
    }
}
