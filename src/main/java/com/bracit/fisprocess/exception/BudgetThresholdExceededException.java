package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;

public class BudgetThresholdExceededException extends FisBusinessException {
    public BudgetThresholdExceededException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "/problems/budget-threshold-exceeded");
    }
}
