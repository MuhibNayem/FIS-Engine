package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResponseDto {

    private UUID originalJournalEntryId;
    @Nullable
    private UUID realizedGainLossJournalEntryId;
    private long realizedDeltaBaseCents;
    private String message;
}
