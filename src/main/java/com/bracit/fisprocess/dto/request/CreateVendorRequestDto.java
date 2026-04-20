package com.bracit.fisprocess.dto.request;

import com.bracit.fisprocess.domain.enums.PaymentTerms;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for creating a new AP Vendor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVendorRequestDto {

    @NotBlank(message = "Vendor code is required")
    @Size(max = 50, message = "Vendor code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Vendor name is required")
    @Size(max = 255, message = "Vendor name must not exceed 255 characters")
    private String name;

    @Nullable
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;

    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    @Builder.Default
    private String currency = "USD";

    @NotNull(message = "Payment terms are required")
    private PaymentTerms paymentTerms;
}
