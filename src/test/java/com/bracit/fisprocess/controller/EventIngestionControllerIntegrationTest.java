package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.IdempotencyStatus;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.IdempotencyLogRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("EventIngestionController Integration Tests")
class EventIngestionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessEntityRepository businessEntityRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private IdempotencyLogRepository idempotencyLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        BusinessEntity tenant = BusinessEntity.builder()
                .name("Event Integration Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .build();
        tenantId = businessEntityRepository.save(tenant).getTenantId();

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
    void shouldAcceptAndAsynchronouslyProcessFinancialEvent() throws Exception {
        String eventId = "EVT-INGEST-" + UUID.randomUUID().toString().substring(0, 8);
        FinancialEventRequestDto request = financialEvent(eventId, 25_000L);

        mockMvc.perform(post("/v1/events")
                .header("X-Tenant-Id", tenantId)
                .header("X-Source-System", "ERP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.ik", is(eventId)));

        waitForCondition(() -> journalEntryRepository.existsByTenantIdAndEventId(tenantId, eventId), 10_000L);

        assertEquals(1L, journalEntryRepository.countByTenantIdAndEventId(tenantId, eventId));
        assertEquals(IdempotencyStatus.COMPLETED, idempotencyLogRepository.findByTenantIdAndEventId(tenantId, eventId)
                .orElseThrow()
                .getStatus());
        assertTrue(outboxEventRepository.countByTenantIdAndEventType(tenantId, "fis.journal.posted") > 0);
    }

    @Test
    void shouldReturnAcceptedForDuplicateSamePayload() throws Exception {
        String eventId = "EVT-DUP-SAME-" + UUID.randomUUID().toString().substring(0, 8);
        FinancialEventRequestDto request = financialEvent(eventId, 30_000L);
        String json = jsonMapper.writeValueAsString(request);

        mockMvc.perform(post("/v1/events")
                .header("X-Tenant-Id", tenantId)
                .header("X-Source-System", "ERP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isAccepted());

        waitForCondition(() -> journalEntryRepository.existsByTenantIdAndEventId(tenantId, eventId), 10_000L);

        mockMvc.perform(post("/v1/events")
                .header("X-Tenant-Id", tenantId)
                .header("X-Source-System", "ERP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ik", is(eventId)));

        assertEquals(1L, journalEntryRepository.countByTenantIdAndEventId(tenantId, eventId));
    }

    @Test
    void shouldReturnConflictForDuplicateDifferentPayload() throws Exception {
        String eventId = "EVT-DUP-DIFF-" + UUID.randomUUID().toString().substring(0, 8);
        FinancialEventRequestDto first = financialEvent(eventId, 10_000L);
        FinancialEventRequestDto second = financialEvent(eventId, 11_000L);

        mockMvc.perform(post("/v1/events")
                .header("X-Tenant-Id", tenantId)
                .header("X-Source-System", "ERP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(first)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/v1/events")
                .header("X-Tenant-Id", tenantId)
                .header("X-Source-System", "ERP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("/problems/duplicate-idempotency-key")));
    }

    private FinancialEventRequestDto financialEvent(String eventId, long amountCents) {
        return FinancialEventRequestDto.builder()
                .eventId(eventId)
                .eventType("JOURNAL_POSTED")
                .occurredAt(OffsetDateTime.now())
                .postedDate(LocalDate.of(2026, 2, 25))
                .description("Financial event")
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
