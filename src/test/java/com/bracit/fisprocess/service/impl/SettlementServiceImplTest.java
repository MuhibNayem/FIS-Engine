package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.dto.request.SettlementRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementServiceImpl Unit Tests")
class SettlementServiceImplTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private JournalEntryService journalEntryService;

    @InjectMocks
    private SettlementServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "reserveAccountCode", "FX_REVAL_RESERVE");
    }

    @Test
    @DisplayName("settle should post realized gain entry when settlement rate increases value")
    void settleShouldPostRealizedGainEntry() {
        UUID tenantId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();

        Account receivable = Account.builder()
                .tenantId(tenantId)
                .code("AR_EUR")
                .accountType(AccountType.ASSET)
                .currencyCode("EUR")
                .build();
        Account reserve = Account.builder().tenantId(tenantId).code("FX_REVAL_RESERVE").accountType(AccountType.ASSET).currencyCode("USD")
                .build();
        Account gain = Account.builder().tenantId(tenantId).code("FX_GAIN").accountType(AccountType.REVENUE).currencyCode("USD").build();
        Account loss = Account.builder().tenantId(tenantId).code("FX_LOSS").accountType(AccountType.EXPENSE).currencyCode("USD").build();

        JournalEntry original = JournalEntry.builder()
                .id(originalId)
                .tenantId(tenantId)
                .postedDate(LocalDate.of(2026, 2, 1))
                .status(JournalStatus.POSTED)
                .transactionCurrency("EUR")
                .baseCurrency("USD")
                .exchangeRate(new BigDecimal("1.10"))
                .createdBy("tester")
                .previousHash("0")
                .hash("h")
                .fiscalYear(2026)
                .sequenceNumber(1L)
                .build();
        original.addLine(JournalLine.builder()
                .account(receivable)
                .amount(10_000L)
                .baseAmount(11_000L)
                .isCredit(false)
                .build());

        when(journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, originalId)).thenReturn(Optional.of(original));
        when(accountRepository.findByTenantIdAndCode(tenantId, "AR_EUR")).thenReturn(Optional.of(receivable));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_REVAL_RESERVE")).thenReturn(Optional.of(reserve));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_GAIN")).thenReturn(Optional.of(gain));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_LOSS")).thenReturn(Optional.of(loss));
        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder()
                        .tenantId(tenantId)
                        .name("Tenant")
                        .baseCurrency("USD")
                        .isActive(true)
                        .build()));
        when(journalEntryService.createJournalEntry(eq(tenantId), any(), eq("FIS_ADMIN")))
                .thenReturn(JournalEntryResponseDto.builder().journalEntryId(UUID.randomUUID()).status(JournalStatus.POSTED).build());

        SettlementRequestDto request = SettlementRequestDto.builder()
                .eventId("SETTLE-1")
                .originalJournalEntryId(originalId)
                .settlementDate(LocalDate.of(2026, 2, 28))
                .settlementRate(new BigDecimal("1.20"))
                .monetaryAccountCode("AR_EUR")
                .gainAccountCode("FX_GAIN")
                .lossAccountCode("FX_LOSS")
                .createdBy("settlement-user")
                .build();

        var result = service.settle(tenantId, request);

        assertThat(result.getRealizedDeltaBaseCents()).isEqualTo(1_000L);
        assertThat(result.getRealizedGainLossJournalEntryId()).isNotNull();

        ArgumentCaptor<com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto> captor = ArgumentCaptor.forClass(
                com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto.class);
        verify(journalEntryService).createJournalEntry(eq(tenantId), captor.capture(), eq("FIS_ADMIN"));
        assertThat(captor.getValue().getTransactionCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("settle should post realized loss entry when settlement rate decreases value")
    void settleShouldPostRealizedLossEntry() {
        UUID tenantId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();

        Account receivable = Account.builder()
                .tenantId(tenantId)
                .code("AR_EUR")
                .accountType(AccountType.ASSET)
                .currencyCode("EUR")
                .build();
        Account reserve = Account.builder().tenantId(tenantId).code("FX_REVAL_RESERVE").accountType(AccountType.ASSET).currencyCode("USD")
                .build();
        Account gain = Account.builder().tenantId(tenantId).code("FX_GAIN").accountType(AccountType.REVENUE).currencyCode("USD").build();
        Account loss = Account.builder().tenantId(tenantId).code("FX_LOSS").accountType(AccountType.EXPENSE).currencyCode("USD").build();

        JournalEntry original = JournalEntry.builder()
                .id(originalId)
                .tenantId(tenantId)
                .status(JournalStatus.POSTED)
                .transactionCurrency("EUR")
                .baseCurrency("USD")
                .exchangeRate(new BigDecimal("1.10"))
                .createdBy("tester")
                .previousHash("0")
                .hash("h")
                .fiscalYear(2026)
                .sequenceNumber(1L)
                .build();
        original.addLine(JournalLine.builder()
                .account(receivable)
                .amount(10_000L)
                .baseAmount(11_000L)
                .isCredit(false)
                .build());

        when(journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, originalId)).thenReturn(Optional.of(original));
        when(accountRepository.findByTenantIdAndCode(tenantId, "AR_EUR")).thenReturn(Optional.of(receivable));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_REVAL_RESERVE")).thenReturn(Optional.of(reserve));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_GAIN")).thenReturn(Optional.of(gain));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_LOSS")).thenReturn(Optional.of(loss));
        when(businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder()
                        .tenantId(tenantId)
                        .name("Tenant")
                        .baseCurrency("USD")
                        .isActive(true)
                        .build()));
        when(journalEntryService.createJournalEntry(eq(tenantId), any(), eq("FIS_ADMIN")))
                .thenReturn(JournalEntryResponseDto.builder().journalEntryId(UUID.randomUUID()).status(JournalStatus.POSTED).build());

        SettlementRequestDto request = SettlementRequestDto.builder()
                .eventId("SETTLE-2")
                .originalJournalEntryId(originalId)
                .settlementDate(LocalDate.of(2026, 2, 28))
                .settlementRate(new BigDecimal("1.05"))
                .monetaryAccountCode("AR_EUR")
                .gainAccountCode("FX_GAIN")
                .lossAccountCode("FX_LOSS")
                .createdBy("settlement-user")
                .build();

        var result = service.settle(tenantId, request);

        assertThat(result.getRealizedDeltaBaseCents()).isEqualTo(-500L);
        assertThat(result.getMessage()).contains("loss");

        ArgumentCaptor<com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto> captor = ArgumentCaptor.forClass(
                com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto.class);
        verify(journalEntryService).createJournalEntry(eq(tenantId), captor.capture(), eq("FIS_ADMIN"));
        assertThat(captor.getValue().getLines()).hasSize(2);
        assertThat(captor.getValue().getLines().get(0).getAccountCode()).isEqualTo("FX_LOSS");
        assertThat(captor.getValue().getLines().get(1).getAccountCode()).isEqualTo("FX_REVAL_RESERVE");
    }

    @Test
    @DisplayName("settle should not post JE when settlement has no realized difference")
    void settleShouldNotPostJeWhenNoDifference() {
        UUID tenantId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();

        Account receivable = Account.builder()
                .tenantId(tenantId)
                .code("AR_EUR")
                .accountType(AccountType.ASSET)
                .currencyCode("EUR")
                .build();
        Account reserve = Account.builder().tenantId(tenantId).code("FX_REVAL_RESERVE").accountType(AccountType.ASSET).currencyCode("USD")
                .build();
        Account gain = Account.builder().tenantId(tenantId).code("FX_GAIN").accountType(AccountType.REVENUE).currencyCode("USD").build();
        Account loss = Account.builder().tenantId(tenantId).code("FX_LOSS").accountType(AccountType.EXPENSE).currencyCode("USD").build();

        JournalEntry original = JournalEntry.builder()
                .id(originalId)
                .tenantId(tenantId)
                .status(JournalStatus.POSTED)
                .transactionCurrency("EUR")
                .baseCurrency("USD")
                .exchangeRate(new BigDecimal("1.10"))
                .createdBy("tester")
                .previousHash("0")
                .hash("h")
                .fiscalYear(2026)
                .sequenceNumber(1L)
                .build();
        original.addLine(JournalLine.builder()
                .account(receivable)
                .amount(10_000L)
                .baseAmount(11_000L)
                .isCredit(false)
                .build());

        when(journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, originalId)).thenReturn(Optional.of(original));
        when(accountRepository.findByTenantIdAndCode(tenantId, "AR_EUR")).thenReturn(Optional.of(receivable));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_REVAL_RESERVE")).thenReturn(Optional.of(reserve));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_GAIN")).thenReturn(Optional.of(gain));
        when(accountRepository.findByTenantIdAndCode(tenantId, "FX_LOSS")).thenReturn(Optional.of(loss));
        SettlementRequestDto request = SettlementRequestDto.builder()
                .eventId("SETTLE-3")
                .originalJournalEntryId(originalId)
                .settlementDate(LocalDate.of(2026, 2, 28))
                .settlementRate(new BigDecimal("1.10"))
                .monetaryAccountCode("AR_EUR")
                .gainAccountCode("FX_GAIN")
                .lossAccountCode("FX_LOSS")
                .createdBy("settlement-user")
                .build();

        var result = service.settle(tenantId, request);

        assertThat(result.getRealizedDeltaBaseCents()).isZero();
        assertThat(result.getRealizedGainLossJournalEntryId()).isNull();
        verify(journalEntryService, never()).createJournalEntry(eq(tenantId), any(), eq("FIS_ADMIN"));
    }
}
