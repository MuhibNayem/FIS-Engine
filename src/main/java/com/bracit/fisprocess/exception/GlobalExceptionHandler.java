package com.bracit.fisprocess.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Global exception handler producing RFC 7807 {@link ProblemDetail} responses.
 * <p>
 * Extends {@link ResponseEntityExceptionHandler} and overrides its
 * {@code handleMethodArgumentNotValid} to customize validation error
 * responses. Also handles all {@link FisBusinessException} subclasses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles all domain-specific business exceptions.
     */
    @ExceptionHandler(FisBusinessException.class)
    public ResponseEntity<ProblemDetail> handleFisBusinessException(FisBusinessException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                ex.getHttpStatus(),
                ex.getMessage());
        problemDetail.setType(URI.create(ex.getTypeUri()));
        problemDetail.setTitle(ex.getHttpStatus().getReasonPhrase());
        return ResponseEntity.status(ex.getHttpStatus()).body(problemDetail);
    }

    /**
     * Overrides the parent handler for Jakarta Validation failures
     * to produce our custom RFC 7807 response format.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(400),
                detail);
        problemDetail.setType(URI.create("/problems/validation-failed"));
        problemDetail.setTitle("Validation Failed");
        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handles malformed JSON / unreadable request payloads and returns
     * RFC 7807-compliant validation errors with a stable type URI.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(400),
                "Failed to read request");
        problemDetail.setType(URI.create("/problems/validation-failed"));
        problemDetail.setTitle("Validation Failed");
        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handles missing required request headers (e.g., X-Tenant-Id).
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(400),
                "Required header '" + ex.getHeaderName() + "' is missing.");
        problemDetail.setType(URI.create("/problems/validation-failed"));
        problemDetail.setTitle("Validation Failed");
        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(500),
                "An unexpected internal error occurred.");
        problemDetail.setType(URI.create("/problems/internal-error"));
        problemDetail.setTitle("Internal Server Error");
        return ResponseEntity.internalServerError().body(problemDetail);
    }
}
