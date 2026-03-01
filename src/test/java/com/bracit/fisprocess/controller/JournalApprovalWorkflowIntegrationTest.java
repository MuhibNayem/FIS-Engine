package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.JournalBatchMode;
import com.bracit.fisprocess.domain.enums.JournalWorkflowStatus;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryBatchRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.repository.JournalWorkflowRepository;
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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "fis.approval.threshold-cents=10000")
@AutoConfigureMockMvc
@DisplayName("Journal Approval Workflow Integration Tests")
class JournalApprovalWorkflowIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired
    private JournalWorkflowRepository journalWorkflowRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        BusinessEntity tenant = BusinessEntity.builder()
                .name("Workflow Integration Test Corp")
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
    void shouldRequireApprovalAndPostOnlyAfterApprove() throws Exception {
        String eventId = "EVT-WF-" + UUID.randomUUID().toString().substring(0, 8);
        CreateJournalEntryRequestDto request = CreateJournalEntryRequestDto.builder()
                .eventId(eventId)
                .postedDate(LocalDate.of(2026, 2, 25))
                .description("Needs approval")
                .transactionCurrency("USD")
                .createdBy("maker-user")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH").amountCents(50_000L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REVENUE").amountCents(50_000L).isCredit(true).build()))
                .build();

        String draftResponse = mockMvc.perform(post("/v1/journal-entries")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString();

        UUID workflowId = UUID.fromString(jsonMapper.readTree(draftResponse).get("journalEntryId").asText());

        assertEquals(0L, journalEntryRepository.countByTenantIdAndEventId(tenantId, eventId));
        assertEquals(JournalWorkflowStatus.DRAFT,
                journalWorkflowRepository.findByTenantIdAndWorkflowId(tenantId, workflowId).orElseThrow().getStatus());

        mockMvc.perform(post("/v1/journal-entries/{id}/submit", workflowId)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"submittedBy":"maker-user"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING_APPROVAL")));

        mockMvc.perform(post("/v1/journal-entries/{id}/approve", workflowId)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvedBy":"maker-user"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", is("/problems/approval-violation")));

        String approveResponse = mockMvc.perform(post("/v1/journal-entries/{id}/approve", workflowId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Actor-Role", "FIS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvedBy":"checker-user"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.postedJournalEntryId", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        UUID postedId = UUID.fromString(jsonMapper.readTree(approveResponse).get("postedJournalEntryId").asText());

        mockMvc.perform(get("/v1/journal-entries/{id}", postedId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("POSTED")))
                .andExpect(jsonPath("$.sequenceNumber", greaterThan(0)))
                .andExpect(jsonPath("$.fiscalYear", is(2026)));
    }

    @Test
    void shouldRejectPostNowBatchWhenAnyEntryRequiresApproval() throws Exception {
        String eventIdSmall = "EVT-BATCH-SMALL-" + UUID.randomUUID().toString().substring(0, 8);
        String eventIdLarge = "EVT-BATCH-LARGE-" + UUID.randomUUID().toString().substring(0, 8);

        CreateJournalEntryRequestDto smallEntry = CreateJournalEntryRequestDto.builder()
                .eventId(eventIdSmall)
                .postedDate(LocalDate.of(2026, 2, 25))
                .description("Below threshold")
                .transactionCurrency("USD")
                .createdBy("maker-user")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH").amountCents(5_000L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REVENUE").amountCents(5_000L).isCredit(true).build()))
                .build();
        CreateJournalEntryRequestDto largeEntry = CreateJournalEntryRequestDto.builder()
                .eventId(eventIdLarge)
                .postedDate(LocalDate.of(2026, 2, 25))
                .description("Above threshold")
                .transactionCurrency("USD")
                .createdBy("maker-user")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH").amountCents(50_000L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REVENUE").amountCents(50_000L).isCredit(true).build()))
                .build();

        CreateJournalEntryBatchRequestDto request = CreateJournalEntryBatchRequestDto.builder()
                .batchMode(JournalBatchMode.POST_NOW)
                .entries(List.of(smallEntry, largeEntry))
                .build();

        mockMvc.perform(post("/v1/journal-entries/batch")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", is("/problems/approval-violation")));

        assertFalse(journalEntryRepository.existsByTenantIdAndEventId(tenantId, eventIdSmall));
        assertFalse(journalEntryRepository.existsByTenantIdAndEventId(tenantId, eventIdLarge));
        assertFalse(journalWorkflowRepository.existsByTenantIdAndEventId(tenantId, eventIdSmall));
        assertFalse(journalWorkflowRepository.existsByTenantIdAndEventId(tenantId, eventIdLarge));
    }
}
