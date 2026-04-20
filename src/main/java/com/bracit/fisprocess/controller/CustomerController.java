package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.dto.request.CreateCustomerRequestDto;
import com.bracit.fisprocess.dto.response.CustomerResponseDto;
import com.bracit.fisprocess.service.InvoiceService;
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
 * REST controller for AR Customer management.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ar/customers")
@RequiredArgsConstructor
@ApiVersion(1)
public class CustomerController {

    private final InvoiceService invoiceService;
    private final ModelMapper modelMapper;

    /**
     * Creates a new AR Customer.
     *
     * @param tenantId the tenant UUID from X-Tenant-Id header
     * @param request  the customer creation details
     * @return 201 Created with the new customer details
     */
    @PostMapping
    public ResponseEntity<CustomerResponseDto> createCustomer(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody CreateCustomerRequestDto request) {
        Customer customer = invoiceService.createCustomer(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(customer));
    }

    /**
     * Retrieves a customer by ID.
     *
     * @param tenantId   the tenant UUID
     * @param customerId the customer UUID
     * @return 200 OK with customer details
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponseDto> getCustomer(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID customerId) {
        Customer customer = invoiceService.getCustomer(tenantId, customerId);
        return ResponseEntity.ok(toResponseDto(customer));
    }

    /**
     * Lists customers for a tenant with optional search filter.
     *
     * @param tenantId the tenant UUID
     * @param search   optional search term (matches code or name)
     * @param pageable pagination parameters
     * @return 200 OK with paginated customer list
     */
    @GetMapping
    public ResponseEntity<Page<CustomerResponseDto>> listCustomers(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<CustomerResponseDto> response = invoiceService.listCustomers(tenantId, search, pageable)
                .map(this::toResponseDto);
        return ResponseEntity.ok(response);
    }

    private CustomerResponseDto toResponseDto(Customer customer) {
        CustomerResponseDto dto = modelMapper.map(customer, CustomerResponseDto.class);
        dto.setCustomerId(customer.getCustomerId());
        dto.setStatus(customer.getStatus().name());
        return dto;
    }
}
