package com.bracit.fisprocess.exception;
import java.util.UUID;
import org.springframework.http.HttpStatus;
public class BankAccountNotFoundException extends FisBusinessException {
    public BankAccountNotFoundException(UUID id) {
        super("Bank account not found: " + id, HttpStatus.NOT_FOUND, "/problems/bank-account-not-found");
    }
}
