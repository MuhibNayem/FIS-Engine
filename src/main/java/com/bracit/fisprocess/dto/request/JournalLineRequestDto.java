package com.bracit.fisprocess.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Request DTO for a single journal line within a Journal Entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalLineRequestDto {

    @NotBlank(message = "accountCode is required")
    private String accountCode;

    @Positive(message = "amountCents must be positive")
    private Long amountCents;

    @JsonProperty("isCredit")
    @JsonAlias("credit")
    private boolean isCredit;

    @Nullable
    private Map<String, String> dimensions;
}
