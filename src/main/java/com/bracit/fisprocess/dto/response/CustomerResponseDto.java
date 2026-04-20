package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for AR Customer details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponseDto {

    private UUID customerId;
    private UUID tenantId;
    private String code;
    private String name;
    private String email;
    private String currency;
    private Long creditLimit;
    private String status;
    private OffsetDateTime createdAt;
}
