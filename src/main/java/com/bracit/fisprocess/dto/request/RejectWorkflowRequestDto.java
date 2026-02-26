package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectWorkflowRequestDto {

    @NotBlank(message = "rejectedBy is required")
    private String rejectedBy;

    @NotBlank(message = "reason is required")
    private String reason;
}
