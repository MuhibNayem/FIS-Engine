package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for updating an existing account.
 * <p>
 * Only the account name and active status can be modified.
 * Account type, code, and currency code are immutable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountRequestDto {

    @Nullable
    @Size(max = 255, message = "Account name must not exceed 255 characters")
    private String name;

    @Nullable
    private Boolean isActive;
}
