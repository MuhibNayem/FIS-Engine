package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.service.ExchangeRateService;
import com.bracit.fisprocess.service.MultiCurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MultiCurrencyServiceImpl implements MultiCurrencyService {

    private final ExchangeRateService exchangeRateService;

    @Override
    public DraftJournalEntry apply(DraftJournalEntry draft) {
        BigDecimal rate = exchangeRateService.resolveRate(
                draft.getTenantId(),
                draft.getTransactionCurrency(),
                draft.getBaseCurrency(),
                draft.getPostedDate());

        List<DraftJournalLine> converted = draft.getLines().stream()
                .map(line -> line.toBuilder()
                        .baseAmountCents(convert(line.getAmountCents(), rate))
                        .build())
                .toList();

        return draft.toBuilder()
                .exchangeRate(rate)
                .lines(converted)
                .build();
    }

    private long convert(long amountCents, BigDecimal rate) {
        return BigDecimal.valueOf(amountCents)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
