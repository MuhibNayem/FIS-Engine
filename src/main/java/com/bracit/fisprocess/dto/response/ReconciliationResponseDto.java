package com.bracit.fisprocess.dto.response;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReconciliationResponseDto {
    private String id; private String tenantId; private String bankAccountId;
    private String startDate; private String endDate; private String reconciledAt;
    private String reconciledBy; private String status;
    private Long totalMatched; private Long totalUnmatched; private Long discrepancy;
}
