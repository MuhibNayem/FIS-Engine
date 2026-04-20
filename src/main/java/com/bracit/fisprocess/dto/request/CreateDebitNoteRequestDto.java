package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Request DTO for creating a new AP Debit Note.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDebitNoteRequestDto {

    @NotNull(message = "Vendor ID is required")
    private UUID vendorId;

    @NotNull(message = "Original bill ID is required")
    private UUID originalBillId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Long amount;

    @Nullable
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
