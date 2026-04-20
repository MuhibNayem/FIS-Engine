package com.bracit.fisprocess.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StartReconciliationRequestDto {
    @NotNull private UUID bankAccountId;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
}
