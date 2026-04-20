package com.bracit.fisprocess.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class BankStatementNotFoundException extends FisBusinessException {
    public BankStatementNotFoundException(UUID statementId) {
        super("Bank statement not found: " + statementId, HttpStatus.NOT_FOUND, "/problems/bank-statement-not-found");
    }
}