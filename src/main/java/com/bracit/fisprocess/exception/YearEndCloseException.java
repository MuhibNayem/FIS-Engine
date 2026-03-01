package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a year-end close operation cannot be performed.
 */
public class YearEndCloseException extends FisBusinessException {

    public YearEndCloseException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "/problems/year-end-close-failed");
    }
}
