package com.bracit.fisprocess.dto.response;
import lombok.*;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BankStatementResponseDto {
    private String id; private String tenantId; private String bankAccountId;
    private String statementDate; private Long openingBalance; private Long closingBalance;
    private String status; private String importedBy; private String createdAt;
    private List<BankStatementLineResponseDto> lines;
}
