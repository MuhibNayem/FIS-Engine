package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingRuleResponseDto {

    private UUID ruleId;
    private String eventType;
    private String description;
    private boolean isActive;
    private int version;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<MappingRuleLineResponseDto> lines;
}
