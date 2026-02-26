package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.service.ExchangeRateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultiCurrencyServiceImpl Unit Tests")
class MultiCurrencyServiceImplTest {

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private MultiCurrencyServiceImpl service;

    @Test
    @DisplayName("apply should convert all lines using HALF_UP rounding")
    void applyShouldConvertWithHalfUpRounding() {
        UUID tenantId = UUID.randomUUID();
        DraftJournalEntry draft = DraftJournalEntry.builder()
                .tenantId(tenantId)
                .transactionCurrency("EUR")
                .baseCurrency("USD")
                .postedDate(LocalDate.of(2026, 2, 1))
                .lines(List.of(
                        DraftJournalLine.builder().accountCode("A").amountCents(101L).isCredit(false).build(),
                        DraftJournalLine.builder().accountCode("B").amountCents(101L).isCredit(true).build()))
                .build();
        when(exchangeRateService.resolveRate(tenantId, "EUR", "USD", LocalDate.of(2026, 2, 1)))
                .thenReturn(new BigDecimal("1.005"));

        DraftJournalEntry result = service.apply(draft);

        assertThat(result.getExchangeRate()).isEqualByComparingTo("1.005");
        assertThat(result.getLines()).hasSize(2);
        assertThat(result.getLines().get(0).getBaseAmountCents()).isEqualTo(102L);
        assertThat(result.getLines().get(1).getBaseAmountCents()).isEqualTo(102L);
    }

    @Test
    @DisplayName("apply should allocate rounding residual by largest remainder per side")
    void applyShouldAllocateRoundingResidualByLargestRemainder() {
        UUID tenantId = UUID.randomUUID();
        DraftJournalEntry draft = DraftJournalEntry.builder()
                .tenantId(tenantId)
                .transactionCurrency("EUR")
                .baseCurrency("USD")
                .postedDate(LocalDate.of(2026, 2, 1))
                .lines(List.of(
                        DraftJournalLine.builder().accountCode("D1").amountCents(1L).isCredit(false).build(),
                        DraftJournalLine.builder().accountCode("D2").amountCents(2L).isCredit(false).build(),
                        DraftJournalLine.builder().accountCode("C1").amountCents(3L).isCredit(true).build()))
                .build();
        when(exchangeRateService.resolveRate(tenantId, "EUR", "USD", LocalDate.of(2026, 2, 1)))
                .thenReturn(new BigDecimal("1.25"));

        DraftJournalEntry result = service.apply(draft);

        long debitBase = result.getLines().stream().filter(line -> !line.isCredit()).mapToLong(DraftJournalLine::getBaseAmountCents).sum();
        long creditBase = result.getLines().stream().filter(DraftJournalLine::isCredit).mapToLong(DraftJournalLine::getBaseAmountCents).sum();

        assertThat(debitBase).isEqualTo(4L);
        assertThat(creditBase).isEqualTo(4L);
        assertThat(result.getLines().get(0).getBaseAmountCents()).isEqualTo(1L);
        assertThat(result.getLines().get(1).getBaseAmountCents()).isEqualTo(3L);
    }

    @Test
    @DisplayName("apply should bypass conversion when transaction currency equals base currency")
    void applyShouldBypassConversionForSameCurrency() {
        UUID tenantId = UUID.randomUUID();
        DraftJournalEntry draft = DraftJournalEntry.builder()
                .tenantId(tenantId)
                .transactionCurrency("usd")
                .baseCurrency("USD")
                .postedDate(LocalDate.of(2026, 2, 1))
                .lines(List.of(
                        DraftJournalLine.builder().accountCode("D").amountCents(1_234L).isCredit(false).build(),
                        DraftJournalLine.builder().accountCode("C").amountCents(1_234L).isCredit(true).build()))
                .build();

        DraftJournalEntry result = service.apply(draft);

        assertThat(result.getExchangeRate()).isEqualByComparingTo("1");
        assertThat(result.getLines().get(0).getBaseAmountCents()).isEqualTo(1_234L);
        assertThat(result.getLines().get(1).getBaseAmountCents()).isEqualTo(1_234L);
        verify(exchangeRateService, never()).resolveRate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
