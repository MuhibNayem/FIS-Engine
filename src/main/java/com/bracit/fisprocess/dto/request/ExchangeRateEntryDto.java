package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateEntryDto {

    @NotBlank(message = "sourceCurrency is required")
    private String sourceCurrency;

    @NotBlank(message = "targetCurrency is required")
    private String targetCurrency;

    @NotNull(message = "rate is required")
    @DecimalMin(value = "0.00000001", message = "rate must be > 0")
    private BigDecimal rate;

    @NotNull(message = "effectiveDate is required")
    private LocalDate effectiveDate;
}
