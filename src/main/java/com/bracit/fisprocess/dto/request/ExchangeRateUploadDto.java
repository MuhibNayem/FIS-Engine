package com.bracit.fisprocess.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateUploadDto {

    @NotEmpty(message = "rates is required")
    @Valid
    private List<ExchangeRateEntryDto> rates;
}
