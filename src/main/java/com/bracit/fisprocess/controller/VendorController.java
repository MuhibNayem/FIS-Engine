package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.Vendor;
import com.bracit.fisprocess.dto.request.CreateVendorRequestDto;
import com.bracit.fisprocess.dto.response.VendorResponseDto;
import com.bracit.fisprocess.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for AP Vendor management.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ap/vendors")
@RequiredArgsConstructor
@ApiVersion(1)
public class VendorController {

    private final BillService billService;
    private final ModelMapper modelMapper;

    /**
     * Creates a new AP Vendor.
     *
     * @param tenantId the tenant UUID from X-Tenant-Id header
     * @param request  the vendor creation details
     * @return 201 Created with the new vendor details
     */
    @PostMapping
    public ResponseEntity<VendorResponseDto> createVendor(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody CreateVendorRequestDto request) {
        Vendor vendor = billService.createVendor(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(vendor));
    }

    /**
     * Retrieves a vendor by ID.
     *
     * @param tenantId  the tenant UUID
     * @param vendorId  the vendor UUID
     * @return 200 OK with vendor details
     */
    @GetMapping("/{vendorId}")
    public ResponseEntity<VendorResponseDto> getVendor(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID vendorId) {
        Vendor vendor = billService.getVendor(tenantId, vendorId);
        return ResponseEntity.ok(toResponseDto(vendor));
    }

    /**
     * Lists vendors for a tenant with optional search filter.
     *
     * @param tenantId the tenant UUID
     * @param search   optional search term (matches code or name)
     * @param pageable pagination parameters
     * @return 200 OK with paginated vendor list
     */
    @GetMapping
    public ResponseEntity<Page<VendorResponseDto>> listVendors(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<VendorResponseDto> response = billService.listVendors(tenantId, search, pageable)
                .map(this::toResponseDto);
        return ResponseEntity.ok(response);
    }

    private VendorResponseDto toResponseDto(Vendor vendor) {
        VendorResponseDto dto = modelMapper.map(vendor, VendorResponseDto.class);
        dto.setVendorId(vendor.getVendorId());
        dto.setStatus(vendor.getStatus().name());
        dto.setPaymentTerms(vendor.getPaymentTerms().name());
        return dto;
    }
}
