package com.bracit.fisprocess.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.jspecify.annotations.Nullable;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegisterBankAccountRequestDto {
    @NotBlank private String accountNumber;
    @NotBlank @Size(max = 100) private String bankName;
    @Nullable @Size(max = 20) private String branchCode;
    @Size(max = 3) @Builder.Default private String currency = "USD";
    @Nullable @Size(max = 50) private String glAccountCode;
}
