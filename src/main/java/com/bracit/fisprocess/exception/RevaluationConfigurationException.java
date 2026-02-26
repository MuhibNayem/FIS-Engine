package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class RevaluationConfigurationException extends FisBusinessException {

    public RevaluationConfigurationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "/problems/revaluation-configuration-invalid");
    }
}
