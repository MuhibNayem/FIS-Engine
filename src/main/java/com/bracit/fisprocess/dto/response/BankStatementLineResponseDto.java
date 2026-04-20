package com.bracit.fisprocess.dto.response;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BankStatementLineResponseDto {
    private String id; private String date; private String description;
    private Long amount; private String reference; private boolean matched;
    private String matchedJournalLineId;
}
