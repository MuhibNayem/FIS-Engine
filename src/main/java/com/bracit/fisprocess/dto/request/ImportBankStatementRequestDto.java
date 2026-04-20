package com.bracit.fisprocess.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImportBankStatementRequestDto {
    @NotNull private UUID bankAccountId;
    @NotNull private LocalDate statementDate;
    @NotNull private Long openingBalance;
    @NotNull private Long closingBalance;
    @NotBlank @Size(max = 100) private String importedBy;
    @NotNull private List<BankStatementLineDto> lines;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BankStatementLineDto {
        @NotNull private LocalDate date;
        @NotBlank @Size(max = 500) private String description;
        @NotNull private Long amount;
        @Nullable @Size(max = 100) private String reference;
    }
}
