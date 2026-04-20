package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a customer with the requested ID cannot be found within the tenant.
 */
public class CustomerNotFoundException extends FisBusinessException {

    public CustomerNotFoundException(UUID customerId) {
        super(
                "Customer with ID '" + customerId + "' not found.",
                HttpStatus.NOT_FOUND,
                "/problems/customer-not-found");
    }
}
