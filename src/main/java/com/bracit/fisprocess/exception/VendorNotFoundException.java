package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a vendor with the requested ID cannot be found within the tenant.
 */
public class VendorNotFoundException extends FisBusinessException {

    public VendorNotFoundException(UUID vendorId) {
        super(
                "Vendor with ID '" + vendorId + "' not found.",
                HttpStatus.NOT_FOUND,
                "/problems/vendor-not-found");
    }
}
