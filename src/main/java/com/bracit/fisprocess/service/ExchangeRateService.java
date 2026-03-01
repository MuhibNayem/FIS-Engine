package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.ExchangeRateUploadDto;
import com.bracit.fisprocess.dto.response.ExchangeRateResponseDto;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExchangeRateService {
    List<ExchangeRateResponseDto> upload(UUID tenantId, ExchangeRateUploadDto request);

    List<ExchangeRateResponseDto> query(
            UUID tenantId, String sourceCurrency, String targetCurrency, @Nullable LocalDate effectiveDate);

    BigDecimal resolveRate(UUID tenantId, String sourceCurrency, String targetCurrency, LocalDate effectiveDate);

    BigDecimal resolveAverageRate(
            UUID tenantId,
            String sourceCurrency,
            String targetCurrency,
            LocalDate fromDate,
            LocalDate toDate);
}
