package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class ApprovalViolationException extends FisBusinessException {

    public ApprovalViolationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "/problems/approval-violation");
    }
}
