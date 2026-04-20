package com.bracit.fisprocess.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for finalizing an invoice (no body required — just triggers finalization).
 */
@Data
@NoArgsConstructor
public class FinalizeInvoiceRequestDto {
    // Intentionally empty — the action is triggered by the POST to /finalize
}
