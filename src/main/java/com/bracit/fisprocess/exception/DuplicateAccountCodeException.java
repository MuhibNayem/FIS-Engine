package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account with the same code already exists
 * within the specified tenant.
 */
public class DuplicateAccountCodeException extends FisBusinessException {

    public DuplicateAccountCodeException(String accountCode) {
        super(
                "Account with code '" + accountCode + "' already exists for this tenant.",
                HttpStatus.CONFLICT,
                "/problems/duplicate-account-code");
    }
}
