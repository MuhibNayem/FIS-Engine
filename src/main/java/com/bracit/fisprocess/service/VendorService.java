package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.Vendor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Orchestrator service for AP Vendor operations.
 */
public interface VendorService {

    /**
     * Creates a new AP Vendor.
     */
    Vendor create(UUID tenantId, com.bracit.fisprocess.dto.request.CreateVendorRequestDto request);

    /**
     * Updates an existing vendor.
     */
    Vendor update(UUID tenantId, UUID vendorId, com.bracit.fisprocess.dto.request.CreateVendorRequestDto request);

    /**
     * Retrieves a vendor by ID, validating tenant ownership.
     */
    Vendor getById(UUID tenantId, UUID vendorId);

    /**
     * Lists vendors for a tenant with optional search filter.
     */
    Page<Vendor> list(UUID tenantId, @Nullable String search, Pageable pageable);
}
