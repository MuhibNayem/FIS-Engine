package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for AP Vendor details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorResponseDto {

    private UUID vendorId;
    private UUID tenantId;
    private String code;
    private String name;
    private String taxId;
    private String currency;
    private String paymentTerms;
    private String status;
    private OffsetDateTime createdAt;
}
