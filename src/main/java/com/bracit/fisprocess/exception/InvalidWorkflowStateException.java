package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class InvalidWorkflowStateException extends FisBusinessException {

    public InvalidWorkflowStateException(String message) {
        super(message, HttpStatus.CONFLICT, "/problems/invalid-workflow-state");
    }
}
