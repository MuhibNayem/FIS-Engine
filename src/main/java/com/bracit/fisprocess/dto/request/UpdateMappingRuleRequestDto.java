package com.bracit.fisprocess.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMappingRuleRequestDto {

    @NotBlank(message = "eventType is required")
    private String eventType;

    @Nullable
    private String description;

    @NotBlank(message = "updatedBy is required")
    private String updatedBy;

    @NotEmpty(message = "At least one mapping line is required")
    @Valid
    private List<MappingRuleLineDto> lines;
}
