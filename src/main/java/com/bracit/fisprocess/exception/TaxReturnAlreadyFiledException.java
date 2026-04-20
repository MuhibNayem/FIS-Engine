package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when attempting to file a tax return that is already filed.
 */
public class TaxReturnAlreadyFiledException extends FisBusinessException {

    public TaxReturnAlreadyFiledException(UUID taxReturnId) {
        super(
                "Tax return '" + taxReturnId + "' is already filed and cannot be modified.",
                HttpStatus.CONFLICT,
                "/problems/tax-return-already-filed");
    }
}
