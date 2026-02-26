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
public class CreateMappingRuleRequestDto {

    @NotBlank(message = "eventType is required")
    private String eventType;

    @Nullable
    private String description;

    @NotBlank(message = "createdBy is required")
    private String createdBy;

    @NotEmpty(message = "At least one mapping line is required")
    @Valid
    private List<MappingRuleLineDto> lines;
}
