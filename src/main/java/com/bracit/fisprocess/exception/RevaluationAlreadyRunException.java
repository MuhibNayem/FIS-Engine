package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class RevaluationAlreadyRunException extends FisBusinessException {

    public RevaluationAlreadyRunException(UUID periodId) {
        super("Revaluation already executed for period '" + periodId + "'.", HttpStatus.CONFLICT,
                "/problems/revaluation-already-run");
    }
}
