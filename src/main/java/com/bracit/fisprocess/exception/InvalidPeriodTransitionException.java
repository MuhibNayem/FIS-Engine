package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class InvalidPeriodTransitionException extends FisBusinessException {
    public InvalidPeriodTransitionException(String detail) {
        super(
                detail,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "/problems/invalid-period-transition");
    }
}
