package com.bracit.fisprocess.dto.request;

import com.bracit.fisprocess.domain.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for creating a new account in the Chart of Accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequestDto {

    @NotBlank(message = "Account code is required")
    @Size(max = 50, message = "Account code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Account name is required")
    @Size(max = 255, message = "Account name must not exceed 255 characters")
    private String name;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private String currencyCode;

    @Nullable
    private String parentAccountCode;
}
