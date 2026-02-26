package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class InvalidReversalException extends FisBusinessException {

    public InvalidReversalException(String message) {
        super(message, HttpStatus.CONFLICT, "/problems/invalid-reversal");
    }
}
