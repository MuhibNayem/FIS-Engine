package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account with the requested code cannot be found
 * within the specified tenant.
 */
public class AccountNotFoundException extends FisBusinessException {

    public AccountNotFoundException(String accountCode) {
        super(
                "Account with code '" + accountCode + "' not found.",
                HttpStatus.NOT_FOUND,
                "/problems/account-not-found");
    }
}
