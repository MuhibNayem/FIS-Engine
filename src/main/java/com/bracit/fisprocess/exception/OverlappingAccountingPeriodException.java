package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class OverlappingAccountingPeriodException extends FisBusinessException {
    public OverlappingAccountingPeriodException() {
        super(
                "Accounting period overlaps with an existing period.",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "/problems/overlapping-accounting-period");
    }
}
