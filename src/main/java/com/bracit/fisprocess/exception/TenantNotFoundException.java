package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the X-Tenant-Id header references a non-existent
 * or inactive Business Entity.
 */
public class TenantNotFoundException extends FisBusinessException {

    public TenantNotFoundException(String tenantId) {
        super(
                "Tenant with ID '" + tenantId + "' not found or inactive.",
                HttpStatus.BAD_REQUEST,
                "/problems/tenant-not-found");
    }
}
