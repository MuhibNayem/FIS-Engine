package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a financial report request contains invalid or missing
 * parameters.
 */
public class ReportParameterException extends FisBusinessException {

    public ReportParameterException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "/problems/report-invalid-params");
    }
}
