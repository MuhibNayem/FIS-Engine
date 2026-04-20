package com.bracit.fisprocess.exception;
import org.springframework.http.HttpStatus;
import java.util.UUID;
public class BudgetNotFoundException extends FisBusinessException {
    public BudgetNotFoundException(UUID id) { super("Budget not found: " + id, HttpStatus.NOT_FOUND, "/problems/budget-not-found"); }
}
