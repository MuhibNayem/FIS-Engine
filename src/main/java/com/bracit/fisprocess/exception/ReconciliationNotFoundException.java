package com.bracit.fisprocess.exception;
import java.util.UUID;
import org.springframework.http.HttpStatus;
public class ReconciliationNotFoundException extends FisBusinessException {
    public ReconciliationNotFoundException(UUID id) {
        super("Reconciliation not found: " + id, HttpStatus.NOT_FOUND, "/problems/reconciliation-not-found");
    }
}
