package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.service.ExchangeRateService;
import com.bracit.fisprocess.service.MultiCurrencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultiCurrencyServiceImpl implements MultiCurrencyService {

    private final ExchangeRateService exchangeRateService;

    @Override
    public DraftJournalEntry apply(DraftJournalEntry draft) {
        if (isSameCurrency(draft.getTransactionCurrency(), draft.getBaseCurrency())) {
            List<DraftJournalLine> passthrough = draft.getLines().stream()
                    .map(line -> line.toBuilder()
                            .baseAmountCents(line.getAmountCents())
                            .build())
                    .toList();
            return draft.toBuilder()
                    .exchangeRate(BigDecimal.ONE)
                    .lines(passthrough)
                    .build();
        }

        BigDecimal rate = exchangeRateService.resolveRate(
                draft.getTenantId(),
                draft.getTransactionCurrency(),
                draft.getBaseCurrency(),
                draft.getPostedDate());

        List<DraftJournalLine> lines = draft.getLines();
        long[] baseAmounts = new long[lines.size()];
        allocateBySide(lines, rate, false, baseAmounts); // debit lines
        allocateBySide(lines, rate, true, baseAmounts);  // credit lines

        List<DraftJournalLine> converted = IntStream.range(0, lines.size())
                .mapToObj(i -> lines.get(i).toBuilder()
                        .baseAmountCents(baseAmounts[i])
                        .build())
                .toList();

        return draft.toBuilder()
                .exchangeRate(rate)
                .lines(converted)
                .build();
    }

    private void allocateBySide(List<DraftJournalLine> lines, BigDecimal rate, boolean creditSide, long[] output) {
        List<LineRounding> rounded = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            DraftJournalLine line = lines.get(i);
            if (line.isCredit() != creditSide) {
                continue;
            }
            BigDecimal raw = BigDecimal.valueOf(line.getAmountCents()).multiply(rate);
            long floor = raw.setScale(0, RoundingMode.DOWN).longValueExact();
            BigDecimal fractional = raw.subtract(BigDecimal.valueOf(floor));
            rounded.add(new LineRounding(i, raw, floor, fractional));
        }
        if (rounded.isEmpty()) {
            return;
        }

        long floorSum = rounded.stream().mapToLong(LineRounding::floor).sum();
        BigDecimal rawTotal = rounded.stream()
                .map(LineRounding::raw)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long targetTotal = rawTotal.setScale(0, RoundingMode.HALF_UP).longValueExact();
        long residual = targetTotal - floorSum;
        if (residual != 0L) {
            log.debug("Applying rounding residual side={} residual={} targetTotal={} floorSum={}",
                    creditSide ? "credit" : "debit",
                    residual,
                    targetTotal,
                    floorSum);
        }

        rounded.sort(Comparator.comparing(LineRounding::fractional).reversed());
        for (int i = 0; i < rounded.size(); i++) {
            LineRounding item = rounded.get(i);
            long amount = item.floor() + (i < residual ? 1L : 0L);
            output[item.index()] = amount;
        }
    }

    private boolean isSameCurrency(String transactionCurrency, String baseCurrency) {
        return transactionCurrency != null
                && baseCurrency != null
                && transactionCurrency.trim().equalsIgnoreCase(baseCurrency.trim());
    }

    private record LineRounding(int index, BigDecimal raw, long floor, BigDecimal fractional) {
    }
}
