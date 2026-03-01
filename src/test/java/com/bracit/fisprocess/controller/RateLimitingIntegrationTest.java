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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "fis.rate-limit.enabled=true",
        "fis.rate-limit.window-seconds=60",
        "fis.rate-limit.requests-per-window=2"
})
@AutoConfigureMockMvc
@DisplayName("Rate Limiting Integration Tests")
class RateLimitingIntegrationTest extends AbstractIntegrationTest {

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

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = businessEntityRepository.save(BusinessEntity.builder()
                .name("Rate Limit Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .build()).getTenantId();

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
                .code("REV")
                .name("Revenue")
                .accountType(AccountType.REVENUE)
                .currencyCode("USD")
                .build());
    }

    @Test
    void shouldReturn429WhenPostingRateLimitExceeded() throws Exception {
        mockMvc.perform(post("/v1/journal-entries")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request("RL-1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/journal-entries")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request("RL-2"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/journal-entries")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request("RL-3"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type", is("/problems/rate-limit-exceeded")));
    }

    private CreateJournalEntryRequestDto request(String eventId) {
        return CreateJournalEntryRequestDto.builder()
                .eventId(eventId + "-" + UUID.randomUUID().toString().substring(0, 8))
                .postedDate(LocalDate.of(2026, 2, 25))
                .transactionCurrency("USD")
                .createdBy("rate-limit-test")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH").amountCents(1_000L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REV").amountCents(1_000L).isCredit(true).build()))
                .build();
    }
}
