package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class PeriodClosedException extends FisBusinessException {
    public PeriodClosedException(String detail) {
        super(
                detail,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "/problems/period-closed");
    }
}
