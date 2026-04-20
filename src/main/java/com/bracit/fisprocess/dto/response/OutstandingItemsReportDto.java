package com.bracit.fisprocess.dto.response;
import lombok.*;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OutstandingItemsReportDto {
    private String bankAccountId; private String asOfDate;
    private List<OutstandingItemDto> outstandingStatementLines;
    private List<OutstandingItemDto> unmatchedJournalLines;
    private Long totalOutstanding;
}
