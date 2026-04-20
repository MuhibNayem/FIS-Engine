package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Vendor;
import com.bracit.fisprocess.dto.request.CreateVendorRequestDto;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.exception.VendorNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.VendorRepository;
import com.bracit.fisprocess.service.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of {@link VendorService} for AP Vendor operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository;
    private final BusinessEntityRepository businessEntityRepository;

    @Override
    @Transactional
    public Vendor create(UUID tenantId, CreateVendorRequestDto request) {
        validateTenantExists(tenantId);
        validateVendorCodeUniqueness(tenantId, request.getCode());

        Vendor vendor = Vendor.builder()
                .tenantId(tenantId)
                .code(request.getCode().trim())
                .name(request.getName().trim())
                .taxId(request.getTaxId())
                .currency(request.getCurrency())
                .paymentTerms(request.getPaymentTerms())
                .status(Vendor.VendorStatus.ACTIVE)
                .build();

        Vendor saved = vendorRepository.save(vendor);
        log.info("Created vendor '{}' for tenant '{}'", saved.getCode(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public Vendor update(UUID tenantId, UUID vendorId, CreateVendorRequestDto request) {
        Vendor vendor = getVendorOrThrow(tenantId, vendorId);

        // Check code uniqueness if code is changing
        if (!vendor.getCode().equals(request.getCode())) {
            validateVendorCodeUniqueness(tenantId, request.getCode());
        }

        vendor.setCode(request.getCode().trim());
        vendor.setName(request.getName().trim());
        vendor.setTaxId(request.getTaxId());
        vendor.setCurrency(request.getCurrency());
        vendor.setPaymentTerms(request.getPaymentTerms());

        Vendor updated = vendorRepository.save(vendor);
        log.info("Updated vendor '{}' for tenant '{}'", updated.getCode(), tenantId);
        return updated;
    }

    @Override
    public Vendor getById(UUID tenantId, UUID vendorId) {
        return getVendorOrThrow(tenantId, vendorId);
    }

    @Override
    public Page<Vendor> list(UUID tenantId, @Nullable String search, Pageable pageable) {
        validateTenantExists(tenantId);
        return vendorRepository.findByTenantIdWithFilters(tenantId, search, pageable);
    }

    // --- Private Helper Methods ---

    private Vendor getVendorOrThrow(UUID tenantId, UUID vendorId) {
        return vendorRepository.findById(vendorId)
                .filter(v -> v.getTenantId().equals(tenantId))
                .orElseThrow(() -> new VendorNotFoundException(vendorId));
    }

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private void validateVendorCodeUniqueness(UUID tenantId, String code) {
        if (vendorRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException(
                    "Vendor code '" + code + "' already exists for this tenant");
        }
    }
}
