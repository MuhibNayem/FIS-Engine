package com.bracit.fisprocess.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MatchLineRequestDto {
    @NotNull private UUID statementLineId;
    @NotNull private UUID journalLineId;
    @NotNull private Long amount;
}
