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
public class ApproveWorkflowRequestDto {

    @NotBlank(message = "approvedBy is required")
    private String approvedBy;
}
