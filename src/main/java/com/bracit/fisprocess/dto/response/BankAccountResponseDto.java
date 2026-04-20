package com.bracit.fisprocess.dto.response;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BankAccountResponseDto {
    private String id; private String tenantId; private String accountNumber;
    private String bankName; private String branchCode; private String currency;
    private String glAccountCode; private String status; private String createdAt;
}
