package com.bracit.fisprocess.exception;
import org.springframework.http.HttpStatus;
import java.util.UUID;
public class ConsolidationGroupNotFoundException extends FisBusinessException {
    public ConsolidationGroupNotFoundException(UUID id) { super("Consolidation group not found: " + id, HttpStatus.NOT_FOUND, "/problems/consolidation-group-not-found"); }
}
