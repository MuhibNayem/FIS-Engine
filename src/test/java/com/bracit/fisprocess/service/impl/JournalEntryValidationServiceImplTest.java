package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.InactiveAccountException;
import com.bracit.fisprocess.exception.UnbalancedEntryException;
import com.bracit.fisprocess.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalEntryValidationService Tests")
class JournalEntryValidationServiceImplTest {

        @Mock
        private AccountRepository accountRepository;

        @InjectMocks
        private JournalEntryValidationServiceImpl validationService;

        private static final UUID TENANT_ID = UUID.randomUUID();

        private Account activeAccount(String code) {
                return Account.builder()
                                .accountId(UUID.randomUUID())
                                .tenantId(TENANT_ID)
                                .code(code)
                                .name("Test " + code)
                                .accountType(AccountType.ASSET)
                                .currencyCode("USD")
                                .isActive(true)
                                .build();
        }

        private DraftJournalEntry balancedDraft() {
                return DraftJournalEntry.builder()
                                .tenantId(TENANT_ID)
                                .eventId("EVT-001")
                                .postedDate(LocalDate.now())
                                .transactionCurrency("USD")
                                .baseCurrency("USD")
                                .exchangeRate(BigDecimal.ONE)
                                .createdBy("test-user")
                                .lines(List.of(
                                                DraftJournalLine.builder()
                                                                .accountCode("CASH")
                                                                .amountCents(10000L)
                                                                .baseAmountCents(10000L)
                                                                .isCredit(false)
                                                                .build(),
                                                DraftJournalLine.builder()
                                                                .accountCode("REVENUE")
                                                                .amountCents(10000L)
                                                                .baseAmountCents(10000L)
                                                                .isCredit(true)
                                                                .build()))
                                .build();
        }

        @Nested
        @DisplayName("Balanced Entry Validation")
        class BalancedEntryTests {

                @Test
                @DisplayName("should pass validation for balanced entry")
                void shouldPassForBalancedEntry() {
                        DraftJournalEntry draft = balancedDraft();

                        when(accountRepository.findByTenantIdAndCode(TENANT_ID, "CASH"))
                                        .thenReturn(Optional.of(activeAccount("CASH")));
                        when(accountRepository.findByTenantIdAndCode(TENANT_ID, "REVENUE"))
                                        .thenReturn(Optional.of(activeAccount("REVENUE")));

                        assertThatCode(() -> validationService.validate(draft))
                                        .doesNotThrowAnyException();
                }

                @Test
                @DisplayName("should reject unbalanced entry (debits > credits)")
                void shouldRejectUnbalancedEntry() {
                        DraftJournalEntry draft = DraftJournalEntry.builder()
                                        .tenantId(TENANT_ID)
                                        .eventId("EVT-002")
                                        .postedDate(LocalDate.now())
                                        .transactionCurrency("USD")
                                        .baseCurrency("USD")
                                        .createdBy("test-user")
                                        .lines(List.of(
                                                        DraftJournalLine.builder()
                                                                        .accountCode("CASH")
                                                                        .amountCents(10000L)
                                                                        .isCredit(false)
                                                                        .build(),
                                                        DraftJournalLine.builder()
                                                                        .accountCode("REVENUE")
                                                                        .amountCents(5000L)
                                                                        .isCredit(true)
                                                                        .build()))
                                        .build();

                        assertThatThrownBy(() -> validationService.validate(draft))
                                        .isInstanceOf(UnbalancedEntryException.class);
                }

                @Test
                @DisplayName("should reject entry with zero amounts")
                void shouldRejectZeroAmountEntry() {
                        DraftJournalEntry draft = DraftJournalEntry.builder()
                                        .tenantId(TENANT_ID)
                                        .eventId("EVT-003")
                                        .postedDate(LocalDate.now())
                                        .transactionCurrency("USD")
                                        .baseCurrency("USD")
                                        .createdBy("test-user")
                                        .lines(List.of(
                                                        DraftJournalLine.builder()
                                                                        .accountCode("CASH")
                                                                        .amountCents(0L)
                                                                        .isCredit(false)
                                                                        .build(),
                                                        DraftJournalLine.builder()
                                                                        .accountCode("REVENUE")
                                                                        .amountCents(0L)
                                                                        .isCredit(true)
                                                                        .build()))
                                        .build();

                        assertThatThrownBy(() -> validationService.validate(draft))
                                        .isInstanceOf(UnbalancedEntryException.class);
                }

                @Test
                @DisplayName("should reject single-sided entry with no credit lines")
                void shouldRejectSingleSidedEntry() {
                        DraftJournalEntry draft = DraftJournalEntry.builder()
                                        .tenantId(TENANT_ID)
                                        .eventId("EVT-004")
                                        .postedDate(LocalDate.now())
                                        .transactionCurrency("USD")
                                        .baseCurrency("USD")
                                        .createdBy("test-user")
                                        .lines(List.of(
                                                        DraftJournalLine.builder()
                                                                        .accountCode("CASH")
                                                                        .amountCents(10000L)
                                                                        .isCredit(false)
                                                                        .build()))
                                        .build();

                        assertThatThrownBy(() -> validationService.validate(draft))
                                        .isInstanceOf(UnbalancedEntryException.class)
                                        .hasMessageContaining("at least one debit line");
                }
        }

        @Nested
        @DisplayName("Account Validation")
        class AccountValidationTests {

                @Test
                @DisplayName("should reject when account code not found")
                void shouldRejectMissingAccount() {
                        DraftJournalEntry draft = balancedDraft();

                        when(accountRepository.findByTenantIdAndCode(TENANT_ID, "CASH"))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> validationService.validate(draft))
                                        .isInstanceOf(AccountNotFoundException.class);
                }

                @Test
                @DisplayName("should reject when account is inactive")
                void shouldRejectInactiveAccount() {
                        DraftJournalEntry draft = balancedDraft();

                        Account inactiveAccount = activeAccount("CASH");
                        inactiveAccount.setActive(false);

                        when(accountRepository.findByTenantIdAndCode(TENANT_ID, "CASH"))
                                        .thenReturn(Optional.of(inactiveAccount));

                        assertThatThrownBy(() -> validationService.validate(draft))
                                        .isInstanceOf(InactiveAccountException.class);
                }
        }
}
