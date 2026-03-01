package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.JournalBatchMode;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryBatchRequestDto;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Journal Batch Concurrency Integration Tests")
class JournalBatchConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private BusinessEntityRepository businessEntityRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;
    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        BusinessEntity tenant = BusinessEntity.builder()
                .name("Batch Concurrency Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .build();
        tenantId = businessEntityRepository.save(tenant).getTenantId();

        accountingPeriodRepository.save(AccountingPeriod.builder()
                .tenantId(tenantId)
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .status(PeriodStatus.OPEN)
                .build());

        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("CASH")
                .name("Cash")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("REVENUE")
                .name("Revenue")
                .accountType(AccountType.REVENUE)
                .currencyCode("USD")
                .build());
    }

    @Test
    @DisplayName("same eventId across concurrent single and batch should be duplicate-safe")
    void sameEventIdAcrossConcurrentSingleAndBatchShouldBeDuplicateSafe() throws Exception {
        String sharedEventId = "EVT-CONC-SHARED-" + UUID.randomUUID().toString().substring(0, 8);
        String batchExtraEventId = "EVT-CONC-BATCH-EXTRA-" + UUID.randomUUID().toString().substring(0, 8);

        CreateJournalEntryRequestDto singleRequest = request(sharedEventId, 5_000L);
        CreateJournalEntryBatchRequestDto batchRequest = CreateJournalEntryBatchRequestDto.builder()
                .batchMode(JournalBatchMode.POST_NOW)
                .entries(List.of(
                        request(sharedEventId, 7_000L),
                        request(batchExtraEventId, 3_000L)))
                .build();

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<HttpCallResult> single = executor.submit(callWhenReleased(start, "/v1/journal-entries", singleRequest));
            Future<HttpCallResult> batch = executor.submit(callWhenReleased(start, "/v1/journal-entries/batch", batchRequest));
            start.countDown();

            HttpCallResult singleResult = single.get();
            HttpCallResult batchResult = batch.get();

            assertThat(singleResult.status == 201 || singleResult.status == 409).isTrue();
            assertThat(batchResult.status == 201 || batchResult.status == 409).isTrue();

            if (singleResult.status == 409) {
                assertThat(problemType(singleResult.body)).isEqualTo("/problems/duplicate-idempotency-key");
            }
            if (batchResult.status == 409) {
                assertThat(problemType(batchResult.body)).isEqualTo("/problems/duplicate-idempotency-key");
                assertThat(journalEntryRepository.existsByTenantIdAndEventId(tenantId, batchExtraEventId)).isFalse();
            }
        }

        assertThat(journalEntryRepository.countByTenantIdAndEventId(tenantId, sharedEventId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("concurrent POST_NOW batches with shared eventId should never partially write loser batch")
    void concurrentPostNowBatchesShouldNotPartiallyWriteLoser() throws Exception {
        String sharedEventId = "EVT-CONC-BATCH-SHARED-" + UUID.randomUUID().toString().substring(0, 8);
        String winnerOrLoserA = "EVT-CONC-BATCH-A-" + UUID.randomUUID().toString().substring(0, 8);
        String winnerOrLoserB = "EVT-CONC-BATCH-B-" + UUID.randomUUID().toString().substring(0, 8);

        CreateJournalEntryBatchRequestDto batchA = CreateJournalEntryBatchRequestDto.builder()
                .batchMode(JournalBatchMode.POST_NOW)
                .entries(List.of(
                        request(sharedEventId, 8_000L),
                        request(winnerOrLoserA, 4_000L)))
                .build();
        CreateJournalEntryBatchRequestDto batchB = CreateJournalEntryBatchRequestDto.builder()
                .batchMode(JournalBatchMode.POST_NOW)
                .entries(List.of(
                        request(sharedEventId, 6_000L),
                        request(winnerOrLoserB, 2_000L)))
                .build();

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<HttpCallResult> a = executor.submit(callWhenReleased(start, "/v1/journal-entries/batch", batchA));
            Future<HttpCallResult> b = executor.submit(callWhenReleased(start, "/v1/journal-entries/batch", batchB));
            start.countDown();

            HttpCallResult resultA = a.get();
            HttpCallResult resultB = b.get();

            assertThat((resultA.status == 201 && resultB.status == 409)
                    || (resultA.status == 409 && resultB.status == 201)).isTrue();

            if (resultA.status == 409) {
                assertThat(problemType(resultA.body)).isEqualTo("/problems/duplicate-idempotency-key");
                assertThat(journalEntryRepository.existsByTenantIdAndEventId(tenantId, winnerOrLoserA)).isFalse();
                assertThat(journalEntryRepository.existsByTenantIdAndEventId(tenantId, winnerOrLoserB)).isTrue();
            } else {
                assertThat(problemType(resultB.body)).isEqualTo("/problems/duplicate-idempotency-key");
                assertThat(journalEntryRepository.existsByTenantIdAndEventId(tenantId, winnerOrLoserB)).isFalse();
                assertThat(journalEntryRepository.existsByTenantIdAndEventId(tenantId, winnerOrLoserA)).isTrue();
            }
        }

        assertThat(journalEntryRepository.countByTenantIdAndEventId(tenantId, sharedEventId)).isEqualTo(1L);
    }

    private Callable<HttpCallResult> callWhenReleased(
            CountDownLatch start,
            String path,
            Object payload) {
        return () -> {
            start.await();
            MvcResult result = mockMvc.perform(post(path)
                            .header("X-Tenant-Id", tenantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(payload)))
                    .andReturn();
            return new HttpCallResult(result.getResponse().getStatus(), result.getResponse().getContentAsString());
        };
    }

    private CreateJournalEntryRequestDto request(String eventId, long amountCents) {
        return CreateJournalEntryRequestDto.builder()
                .eventId(eventId)
                .postedDate(LocalDate.of(2026, 2, 25))
                .transactionCurrency("USD")
                .createdBy("batch-concurrency-test")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH").amountCents(amountCents).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REVENUE").amountCents(amountCents).isCredit(true).build()))
                .build();
    }

    private String problemType(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return "";
        }
        JsonNode root = jsonMapper.readTree(body);
        return root.path("type").asText();
    }

    private record HttpCallResult(int status, String body) {
    }
}
