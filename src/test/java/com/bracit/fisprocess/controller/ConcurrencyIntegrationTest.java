package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concurrency test: 100 Virtual Threads post to the same account concurrently.
 * Verifies no balance corruption occurs under concurrent writes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Concurrency Integration Test")
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private BusinessEntityRepository businessEntityRepository;

        @Autowired
        private AccountRepository accountRepository;
        @Autowired
        private AccountingPeriodRepository accountingPeriodRepository;
        @Autowired
        private JournalEntryRepository journalEntryRepository;

        @Autowired
        private JsonMapper jsonMapper;
        private UUID tenantId;

        @BeforeEach
        void setUp() {
                BusinessEntity tenant = BusinessEntity.builder()
                                .name("Concurrency Test Corp")
                                .baseCurrency("USD")
                                .isActive(true)
                                .build();
                BusinessEntity savedTenant = businessEntityRepository.save(tenant);
                tenantId = savedTenant.getTenantId();

                accountingPeriodRepository.save(AccountingPeriod.builder()
                                .tenantId(tenantId)
                                .name("2026-02")
                                .startDate(LocalDate.of(2026, 2, 1))
                                .endDate(LocalDate.of(2026, 2, 28))
                                .status(PeriodStatus.OPEN)
                                .build());

                accountRepository.save(Account.builder()
                                .tenantId(tenantId)
                                .code("HOT-CASH")
                                .name("Hot Cash Account")
                                .accountType(AccountType.ASSET)
                                .currencyCode("USD")
                                .currentBalance(0L)
                                .build());

                accountRepository.save(Account.builder()
                                .tenantId(tenantId)
                                .code("HOT-REVENUE")
                                .name("Hot Revenue Account")
                                .accountType(AccountType.REVENUE)
                                .currencyCode("USD")
                                .currentBalance(0L)
                                .build());
        }

        @Test
        @DisplayName("100 concurrent JEs to same account should produce correct final balance")
        void concurrentWritesShouldProduceCorrectBalance() throws Exception {
                int threadCount = 100;
                long amountPerEntry = 1000L; // 10.00 per entry

                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failCount = new AtomicInteger(0);

                try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<Future<?>> futures = new ArrayList<>();

                        for (int i = 0; i < threadCount; i++) {
                                final int index = i;
                                futures.add(executor.submit(() -> {
                                        try {
                                                String eventId = "CONC-" + index + "-"
                                                                + UUID.randomUUID().toString().substring(0, 8);
                                                CreateJournalEntryRequestDto request = CreateJournalEntryRequestDto
                                                                .builder()
                                                                .eventId(eventId)
                                                                .postedDate(LocalDate.of(2026, 2, 25))
                                                                .transactionCurrency("USD")
                                                                .createdBy("concurrency-test")
                                                                .lines(List.of(
                                                                                JournalLineRequestDto.builder()
                                                                                                .accountCode("HOT-CASH")
                                                                                                .amountCents(amountPerEntry)
                                                                                                .isCredit(false)
                                                                                                .build(),
                                                                                JournalLineRequestDto.builder()
                                                                                                .accountCode("HOT-REVENUE")
                                                                                                .amountCents(amountPerEntry)
                                                                                                .isCredit(true)
                                                                                                .build()))
                                                                .build();

                                                mockMvc.perform(post("/v1/journal-entries")
                                                                .header("X-Tenant-Id", tenantId.toString())
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(jsonMapper.writeValueAsString(request)))
                                                                .andExpect(status().isCreated());

                                                successCount.incrementAndGet();
                                        } catch (Exception e) {
                                                failCount.incrementAndGet();
                                        }
                                }));
                        }

                        // Wait for all to complete
                        for (Future<?> future : futures) {
                                future.get();
                        }
                }

                // Verify final balances
                Account cashAccount = accountRepository
                                .findByTenantIdAndCode(tenantId, "HOT-CASH")
                                .orElseThrow();
                Account revenueAccount = accountRepository
                                .findByTenantIdAndCode(tenantId, "HOT-REVENUE")
                                .orElseThrow();

                long expectedCashBalance = successCount.get() * amountPerEntry;
                long expectedRevenueBalance = successCount.get() * amountPerEntry;

                assertThat(cashAccount.getCurrentBalance())
                                .as("ASSET account (debit-normal) should increase by %d entries × %d each",
                                                successCount.get(), amountPerEntry)
                                .isEqualTo(expectedCashBalance);

                assertThat(revenueAccount.getCurrentBalance())
                                .as("REVENUE account (credit-normal) should increase by %d entries × %d each",
                                                successCount.get(), amountPerEntry)
                                .isEqualTo(expectedRevenueBalance);

                assertThat(successCount.get())
                                .as("All 100 entries should succeed")
                                .isEqualTo(threadCount);

                assertThat(failCount.get())
                                .as("No entries should fail")
                                .isZero();

                assertHashChainContinuity(successCount.get());
        }

        @Test
        @DisplayName("concurrent multi-account postings with reversed line order should avoid deadlocks")
        void concurrentReversedOrderShouldAvoidDeadlocks() throws Exception {
                int threadCount = 100;
                long amountPerEntry = 1000L;
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failCount = new AtomicInteger(0);

                try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<Future<?>> futures = new ArrayList<>();

                        for (int i = 0; i < threadCount; i++) {
                                final int index = i;
                                futures.add(executor.submit(() -> {
                                        try {
                                                boolean reverseOrder = index % 2 == 0;
                                                CreateJournalEntryRequestDto request = buildHotAccountRequest(
                                                                "CONC-DL-" + index + "-"
                                                                                + UUID.randomUUID().toString()
                                                                                                .substring(0, 8),
                                                                amountPerEntry,
                                                                reverseOrder);

                                                mockMvc.perform(post("/v1/journal-entries")
                                                                .header("X-Tenant-Id", tenantId.toString())
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(jsonMapper.writeValueAsString(request)))
                                                                .andExpect(status().isCreated());
                                                successCount.incrementAndGet();
                                        } catch (Exception e) {
                                                failCount.incrementAndGet();
                                        }
                                }));
                        }

                        for (Future<?> future : futures) {
                                future.get();
                        }
                }

                Account cashAccount = accountRepository
                                .findByTenantIdAndCode(tenantId, "HOT-CASH")
                                .orElseThrow();
                Account revenueAccount = accountRepository
                                .findByTenantIdAndCode(tenantId, "HOT-REVENUE")
                                .orElseThrow();

                long expectedBalance = successCount.get() * amountPerEntry;

                assertThat(successCount.get())
                                .as("All concurrent postings should succeed without deadlock")
                                .isEqualTo(threadCount);
                assertThat(failCount.get())
                                .as("No postings should fail")
                                .isZero();
                assertThat(cashAccount.getCurrentBalance())
                                .as("Cash balance should match number of successful postings")
                                .isEqualTo(expectedBalance);
                assertThat(revenueAccount.getCurrentBalance())
                                .as("Revenue balance should match number of successful postings")
                                .isEqualTo(expectedBalance);

                assertHashChainContinuity(successCount.get());
        }

        private CreateJournalEntryRequestDto buildHotAccountRequest(String eventId, long amountPerEntry,
                        boolean reverseOrder) {
                JournalLineRequestDto debitCash = JournalLineRequestDto.builder()
                                .accountCode("HOT-CASH")
                                .amountCents(amountPerEntry)
                                .isCredit(false)
                                .build();

                JournalLineRequestDto creditRevenue = JournalLineRequestDto.builder()
                                .accountCode("HOT-REVENUE")
                                .amountCents(amountPerEntry)
                                .isCredit(true)
                                .build();

                List<JournalLineRequestDto> lines = reverseOrder
                                ? List.of(creditRevenue, debitCash)
                                : List.of(debitCash, creditRevenue);

                return CreateJournalEntryRequestDto.builder()
                                .eventId(eventId)
                                .postedDate(LocalDate.of(2026, 2, 25))
                                .transactionCurrency("USD")
                                .createdBy("concurrency-test")
                                .lines(lines)
                                .build();
        }

        private void assertHashChainContinuity(int expectedEntryCount) {
                var entries = journalEntryRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);

                assertThat(entries)
                                .as("Expected one journal entry per successful post")
                                .hasSize(expectedEntryCount);

                String previousHash = "0";
                for (var entry : entries) {
                        assertThat(entry.getPreviousHash())
                                        .as("previousHash should link to prior hash for entry %s", entry.getId())
                                        .isEqualTo(previousHash);
                        previousHash = entry.getHash();
                }
        }
}
