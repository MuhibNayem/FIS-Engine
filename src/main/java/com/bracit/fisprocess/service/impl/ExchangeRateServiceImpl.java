package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.ExchangeRate;
import com.bracit.fisprocess.dto.request.ExchangeRateEntryDto;
import com.bracit.fisprocess.dto.request.ExchangeRateUploadDto;
import com.bracit.fisprocess.dto.response.ExchangeRateResponseDto;
import com.bracit.fisprocess.exception.ExchangeRateNotFoundException;
import com.bracit.fisprocess.repository.ExchangeRateRepository;
import com.bracit.fisprocess.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    @Override
    @Transactional
    public List<ExchangeRateResponseDto> upload(UUID tenantId, ExchangeRateUploadDto request) {
        List<ExchangeRate> saved = request.getRates().stream()
                .map(rate -> upsert(tenantId, rate))
                .toList();
        return saved.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExchangeRateResponseDto> query(
            UUID tenantId, String sourceCurrency, String targetCurrency, @Nullable LocalDate effectiveDate) {
        return exchangeRateRepository.query(
                tenantId,
                normalizeCurrency(sourceCurrency),
                normalizeCurrency(targetCurrency),
                effectiveDate).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal resolveRate(UUID tenantId, String sourceCurrency, String targetCurrency, LocalDate effectiveDate) {
        String source = normalizeCurrency(sourceCurrency);
        String target = normalizeCurrency(targetCurrency);
        if (source.equals(target)) {
            return BigDecimal.ONE;
        }

        return exchangeRateRepository.findByTenantIdAndSourceCurrencyAndTargetCurrencyAndEffectiveDate(
                tenantId, source, target, effectiveDate)
                .map(ExchangeRate::getRate)
                .orElseGet(() -> exchangeRateRepository.findLatestPrior(tenantId, source, target, effectiveDate).stream()
                        .findFirst()
                        .map(ExchangeRate::getRate)
                        .orElseThrow(() -> new ExchangeRateNotFoundException(source, target)));
    }

    private ExchangeRate upsert(UUID tenantId, ExchangeRateEntryDto rate) {
        String source = normalizeCurrency(rate.getSourceCurrency());
        String target = normalizeCurrency(rate.getTargetCurrency());
        ExchangeRate entity = exchangeRateRepository
                .findByTenantIdAndSourceCurrencyAndTargetCurrencyAndEffectiveDate(
                        tenantId, source, target, rate.getEffectiveDate())
                .orElse(ExchangeRate.builder()
                        .tenantId(tenantId)
                        .sourceCurrency(source)
                        .targetCurrency(target)
                        .effectiveDate(rate.getEffectiveDate())
                        .build());
        entity.setRate(rate.getRate());
        return exchangeRateRepository.save(entity);
    }

    private ExchangeRateResponseDto toResponse(ExchangeRate rate) {
        return ExchangeRateResponseDto.builder()
                .rateId(rate.getRateId())
                .sourceCurrency(rate.getSourceCurrency())
                .targetCurrency(rate.getTargetCurrency())
                .rate(rate.getRate())
                .effectiveDate(rate.getEffectiveDate())
                .build();
    }

    private String normalizeCurrency(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
