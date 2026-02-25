package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Abstract base exception for all FIS domain-specific errors.
 * <p>
 * Subclasses define a specific {@link HttpStatus} and a URI type
 * for RFC 7807 {@code ProblemDetail} responses.
 */
public abstract class FisBusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String typeUri;

    protected FisBusinessException(String message, HttpStatus httpStatus, String typeUri) {
        super(message);
        this.httpStatus = httpStatus;
        this.typeUri = typeUri;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getTypeUri() {
        return typeUri;
    }
}
