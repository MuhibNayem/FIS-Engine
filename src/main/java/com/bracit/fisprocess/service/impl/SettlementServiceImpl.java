package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.SettlementRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.dto.response.SettlementResponseDto;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.JournalEntryNotFoundException;
import com.bracit.fisprocess.exception.RevaluationConfigurationException;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryService journalEntryService;
    @Value("${fis.revaluation.reserve-account-code:FX_REVAL_RESERVE}")
    private String reserveAccountCode;

    @Override
    @Transactional
    public SettlementResponseDto settle(UUID tenantId, SettlementRequestDto request) {
        JournalEntry original = journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, request.getOriginalJournalEntryId())
                .orElseThrow(() -> new JournalEntryNotFoundException(request.getOriginalJournalEntryId()));

        Account monetaryAccount = accountRepository.findByTenantIdAndCode(tenantId, request.getMonetaryAccountCode())
                .orElseThrow(() -> new AccountNotFoundException(request.getMonetaryAccountCode()));
        validateAccountExists(tenantId, reserveAccountCode);
        validateAccountExists(tenantId, request.getGainAccountCode());
        validateAccountExists(tenantId, request.getLossAccountCode());

        long netAmountCents = 0L;
        long carryingBaseCents = 0L;
        for (JournalLine line : original.getLines()) {
            if (!line.getAccount().getCode().equals(request.getMonetaryAccountCode())) {
                continue;
            }
            long sign = lineSign(line, monetaryAccount);
            netAmountCents += sign * line.getAmount();
            carryingBaseCents += sign * line.getBaseAmount();
        }
        if (netAmountCents == 0L) {
            throw new RevaluationConfigurationException(
                    "No settlement exposure found on account '" + request.getMonetaryAccountCode() + "'.");
        }

        long settlementBaseCents = BigDecimal.valueOf(netAmountCents)
                .multiply(request.getSettlementRate())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long delta = settlementBaseCents - carryingBaseCents;

        if (delta == 0L) {
            return SettlementResponseDto.builder()
                    .originalJournalEntryId(original.getId())
                    .realizedDeltaBaseCents(0L)
                    .message("No realized FX difference for settlement.")
                    .build();
        }

        boolean gain = delta > 0L;
        long amount = Math.abs(delta);
        String counterAccount = gain ? request.getGainAccountCode() : request.getLossAccountCode();
        String reserveAccount = reserveAccountCode;
        String settlementCurrency = resolveBaseCurrency(tenantId, reserveAccount);

        JournalEntryResponseDto posted = journalEntryService.createJournalEntry(
                tenantId,
                CreateJournalEntryRequestDto.builder()
                        .eventId(request.getEventId())
                        .postedDate(request.getSettlementDate())
                        .description("Realized FX settlement adjustment for JE " + original.getId())
                        .referenceId("SETTLE-" + original.getId())
                        .transactionCurrency(settlementCurrency)
                        .createdBy(request.getCreatedBy())
                        .lines(java.util.List.of(
                                JournalLineRequestDto.builder()
                                        .accountCode(gain ? reserveAccount : counterAccount)
                                        .amountCents(amount)
                                        .isCredit(false)
                                        .build(),
                                JournalLineRequestDto.builder()
                                        .accountCode(gain ? counterAccount : reserveAccount)
                                        .amountCents(amount)
                                        .isCredit(true)
                                        .build()))
                        .build(),
                "FIS_ADMIN");

        return SettlementResponseDto.builder()
                .originalJournalEntryId(original.getId())
                .realizedGainLossJournalEntryId(posted.getJournalEntryId())
                .realizedDeltaBaseCents(delta)
                .message(gain ? "Realized FX gain posted." : "Realized FX loss posted.")
                .build();
    }

    private long lineSign(JournalLine line, Account account) {
        boolean debitNormal = account.getAccountType() == AccountType.ASSET || account.getAccountType() == AccountType.EXPENSE;
        if (account.isContra()) {
            debitNormal = !debitNormal;
        }
        return (line.isCredit() == debitNormal) ? -1L : 1L;
    }

    private void validateAccountExists(UUID tenantId, String code) {
        accountRepository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new AccountNotFoundException(code));
    }

    private String resolveBaseCurrency(UUID tenantId, String anyAccountCode) {
        Account account = accountRepository.findByTenantIdAndCode(tenantId, anyAccountCode)
                .orElseThrow(() -> new AccountNotFoundException(anyAccountCode));
        return account.getCurrencyCode();
    }
}
