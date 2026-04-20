package com.bracit.fisprocess.exception;
import org.springframework.http.HttpStatus;
import java.util.UUID;
public class EmployeeNotFoundException extends FisBusinessException {
    public EmployeeNotFoundException(UUID id) { super("Employee not found: " + id, HttpStatus.NOT_FOUND, "/problems/employee-not-found"); }
}
