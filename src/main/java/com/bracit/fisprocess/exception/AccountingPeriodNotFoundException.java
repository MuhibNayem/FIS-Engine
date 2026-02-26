package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class AccountingPeriodNotFoundException extends FisBusinessException {
    public AccountingPeriodNotFoundException() {
        super(
                "No accounting period found for the requested posting date.",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "/problems/accounting-period-not-found");
    }
}
