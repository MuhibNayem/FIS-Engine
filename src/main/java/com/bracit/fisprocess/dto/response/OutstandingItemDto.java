package com.bracit.fisprocess.dto.response;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OutstandingItemDto {
    private String type; // STATEMENT or JOURNAL
    private String id; private String date; private String description;
    private Long amount; private String reference;
}
