package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.IdempotencyStatus;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.IdempotencyLogRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Event Ingestion Redis Fallback Integration Tests")
class EventIngestionRedisFallbackIntegrationTest extends AbstractIntegrationTest {

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
    private IdempotencyLogRepository idempotencyLogRepository;
    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenThrow(new RuntimeException("redis unavailable"));
        org.mockito.Mockito.doThrow(new RuntimeException("redis unavailable"))
                .when(valueOperations).set(any(), any(), any());

        BusinessEntity tenant = BusinessEntity.builder()
                .name("Redis Fallback Test Corp")
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
    void shouldProcessEventUsingPostgresFallbackWhenRedisIsDown() throws Exception {
        String eventId = "EVT-REDIS-FALLBACK-" + UUID.randomUUID().toString().substring(0, 8);
        FinancialEventRequestDto request = financialEvent(eventId, 20_000L);

        mockMvc.perform(post("/v1/events")
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Source-System", "ERP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.ik", is(eventId)));

        waitForCondition(() -> journalEntryRepository.existsByTenantIdAndEventId(tenantId, eventId), 10_000L);
        waitForCondition(() -> idempotencyLogRepository.findByTenantIdAndEventId(tenantId, eventId)
                .map(log -> log.getStatus() == IdempotencyStatus.COMPLETED)
                .orElse(false), 10_000L);

        assertEquals(1L, journalEntryRepository.countByTenantIdAndEventId(tenantId, eventId));
        assertEquals(IdempotencyStatus.COMPLETED, idempotencyLogRepository.findByTenantIdAndEventId(tenantId, eventId)
                .orElseThrow()
                .getStatus());
    }

    private FinancialEventRequestDto financialEvent(String eventId, long amountCents) {
        return FinancialEventRequestDto.builder()
                .eventId(eventId)
                .eventType("JOURNAL_POSTED")
                .occurredAt(OffsetDateTime.now())
                .postedDate(LocalDate.of(2026, 2, 25))
                .description("Redis fallback test event")
                .referenceId("REF-" + eventId)
                .transactionCurrency("USD")
                .createdBy("event-test")
                .lines(List.of(
                        JournalLineRequestDto.builder()
                                .accountCode("CASH")
                                .amountCents(amountCents)
                                .isCredit(false)
                                .build(),
                        JournalLineRequestDto.builder()
                                .accountCode("REVENUE")
                                .amountCents(amountCents)
                                .isCredit(true)
                                .build()))
                .build();
    }

    private void waitForCondition(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Condition not met within timeout");
    }
}
