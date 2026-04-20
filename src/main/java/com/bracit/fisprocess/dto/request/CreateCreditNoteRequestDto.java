package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a Credit Note.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCreditNoteRequestDto {

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    @NotNull(message = "Original invoice ID is required")
    private UUID originalInvoiceId;

    @NotNull(message = "Credit note amount is required")
    @Positive(message = "Credit note amount must be positive")
    private Long amount;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
