package com.bracit.fisprocess.dto.response;

import com.bracit.fisprocess.domain.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for account details including the current balance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponseDto {

    private UUID accountId;
    private String code;
    private String name;
    private AccountType accountType;
    private String currencyCode;
    private Long currentBalanceCents;
    private String formattedBalance;
    private boolean isActive;
    private boolean contra;

    @Nullable
    private String parentAccountCode;

    @Nullable
    private Long aggregatedBalanceCents;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
