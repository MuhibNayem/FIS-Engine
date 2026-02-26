package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class ExchangeRateNotFoundException extends FisBusinessException {
    public ExchangeRateNotFoundException(String sourceCurrency, String targetCurrency) {
        super(
                "No exchange rate found for " + sourceCurrency + " -> " + targetCurrency + " on or before posting date.",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "/problems/exchange-rate-not-found");
    }
}
