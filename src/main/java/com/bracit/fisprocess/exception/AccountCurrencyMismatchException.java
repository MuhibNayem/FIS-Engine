package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class AccountCurrencyMismatchException extends FisBusinessException {
    public AccountCurrencyMismatchException(String accountCode, String accountCurrency, String transactionCurrency) {
        super(
                "Account '" + accountCode + "' currency '" + accountCurrency
                        + "' does not match transaction currency '" + transactionCurrency + "'.",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "/problems/account-currency-mismatch");
    }
}
