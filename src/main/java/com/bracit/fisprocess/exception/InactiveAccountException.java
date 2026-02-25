package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a journal line references an inactive account.
 */
public class InactiveAccountException extends FisBusinessException {

    public InactiveAccountException(String accountCode) {
        super(
                String.format("Account '%s' is inactive and cannot be used in journal entries", accountCode),
                HttpStatus.UNPROCESSABLE_ENTITY,
                "/problems/inactive-account");
    }
}
