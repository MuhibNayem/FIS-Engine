package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.ExchangeRate;
import com.bracit.fisprocess.dto.request.ExchangeRateEntryDto;
import com.bracit.fisprocess.dto.request.ExchangeRateUploadDto;
import com.bracit.fisprocess.exception.ExchangeRateNotFoundException;
import com.bracit.fisprocess.repository.ExchangeRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRateServiceImpl Unit Tests")
class ExchangeRateServiceImplTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private ExchangeRateServiceImpl service;

    @Test
    @DisplayName("resolveRate should return ONE for same currency")
    void resolveRateShouldReturnOneForSameCurrency() {
        BigDecimal result = service.resolveRate(UUID.randomUUID(), "usd", "USD", LocalDate.now());
        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("resolveRate should fallback to latest prior rate when exact date not found")
    void resolveRateShouldFallbackToPriorDate() {
        UUID tenantId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 2, 28);
        when(exchangeRateRepository.findByTenantIdAndSourceCurrencyAndTargetCurrencyAndEffectiveDate(
                tenantId, "EUR", "USD", date)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findLatestPrior(tenantId, "EUR", "USD", date))
                .thenReturn(List.of(ExchangeRate.builder().rate(new BigDecimal("1.12")).build()));

        BigDecimal result = service.resolveRate(tenantId, "eur", "usd", date);

        assertThat(result).isEqualByComparingTo("1.12");
    }

    @Test
    @DisplayName("resolveRate should throw when no rate exists")
    void resolveRateShouldThrowWhenMissing() {
        UUID tenantId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 2, 28);
        when(exchangeRateRepository.findByTenantIdAndSourceCurrencyAndTargetCurrencyAndEffectiveDate(
                tenantId, "EUR", "USD", date)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findLatestPrior(tenantId, "EUR", "USD", date)).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveRate(tenantId, "EUR", "USD", date))
                .isInstanceOf(ExchangeRateNotFoundException.class);
    }

    @Test
    @DisplayName("upload should upsert and normalize currency codes")
    void uploadShouldUpsertAndNormalize() {
        UUID tenantId = UUID.randomUUID();
        ExchangeRateUploadDto request = ExchangeRateUploadDto.builder()
                .rates(List.of(ExchangeRateEntryDto.builder()
                        .sourceCurrency("eur")
                        .targetCurrency("usd")
                        .effectiveDate(LocalDate.of(2026, 2, 1))
                        .rate(new BigDecimal("1.1"))
                        .build()))
                .build();
        when(exchangeRateRepository.findByTenantIdAndSourceCurrencyAndTargetCurrencyAndEffectiveDate(
                tenantId, "EUR", "USD", LocalDate.of(2026, 2, 1))).thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.upload(tenantId, request);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getSourceCurrency()).isEqualTo("EUR");
        assertThat(response.get(0).getTargetCurrency()).isEqualTo("USD");
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }
}

