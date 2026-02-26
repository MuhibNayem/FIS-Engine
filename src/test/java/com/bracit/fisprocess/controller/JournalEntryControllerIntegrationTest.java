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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Journal Entry REST API endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("JournalEntryController Integration Tests")
class JournalEntryControllerIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private BusinessEntityRepository businessEntityRepository;

        @Autowired
        private AccountRepository accountRepository;
        @Autowired
        private AccountingPeriodRepository accountingPeriodRepository;

        @Autowired
        private JsonMapper jsonMapper;

        @Autowired
        private JdbcTemplate jdbcTemplate;
        private UUID tenantId;

        @BeforeEach
        void setUp() {
                BusinessEntity tenant = BusinessEntity.builder()
                                .name("JE Integration Test Corp")
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

                // Create test accounts
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
                                .name("Sales Revenue")
                                .accountType(AccountType.REVENUE)
                                .currencyCode("USD")
                                .build());

                accountRepository.save(Account.builder()
                                .tenantId(tenantId)
                                .code("INACTIVE")
                                .name("Closed Account")
                                .accountType(AccountType.EXPENSE)
                                .currencyCode("USD")
                                .isActive(false)
                                .build());

                accountRepository.save(Account.builder()
                                .tenantId(tenantId)
                                .code("EXPENSE")
                                .name("Operating Expense")
                                .accountType(AccountType.EXPENSE)
                                .currencyCode("USD")
                                .build());

                accountRepository.save(Account.builder()
                                .tenantId(tenantId)
                                .code("ACC_DEP")
                                .name("Accumulated Depreciation")
                                .accountType(AccountType.ASSET)
                                .currencyCode("USD")
                                .isContra(true)
                                .build());
        }

        private String toJson(Object obj) throws Exception {
                return jsonMapper.writeValueAsString(obj);
        }

        private CreateJournalEntryRequestDto balancedRequest(String eventId) {
                return CreateJournalEntryRequestDto.builder()
                                .eventId(eventId)
                                .postedDate(LocalDate.of(2026, 2, 25))
                                .description("Test entry")
                                .transactionCurrency("USD")
                                .createdBy("integration-test")
                                .lines(List.of(
                                                JournalLineRequestDto.builder()
                                                                .accountCode("CASH")
                                                                .amountCents(50000L)
                                                                .isCredit(false)
                                                                .build(),
                                                JournalLineRequestDto.builder()
                                                                .accountCode("REVENUE")
                                                                .amountCents(50000L)
                                                                .isCredit(true)
                                                                .build()))
                                .build();
        }

        private CreateJournalEntryRequestDto expenseRequest(String eventId) {
                return CreateJournalEntryRequestDto.builder()
                                .eventId(eventId)
                                .postedDate(LocalDate.of(2026, 2, 25))
                                .description("Expense entry")
                                .transactionCurrency("USD")
                                .createdBy("integration-test")
                                .lines(List.of(
                                                JournalLineRequestDto.builder()
                                                                .accountCode("EXPENSE")
                                                                .amountCents(50000L)
                                                                .isCredit(false)
                                                                .build(),
                                                JournalLineRequestDto.builder()
                                                                .accountCode("CASH")
                                                                .amountCents(50000L)
                                                                .isCredit(true)
                                                                .build()))
                                .build();
        }

        // --- POST /v1/journal-entries ---

        @Nested
        @DisplayName("POST /v1/journal-entries")
        class CreateJournalEntryTests {

                @Test
                @DisplayName("should return 201 for balanced entry with correct response")
                void shouldCreateBalancedEntry() throws Exception {
                        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 8);

                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(balancedRequest(eventId))))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.journalEntryId", notNullValue()))
                                        .andExpect(jsonPath("$.status", is("POSTED")))
                                        .andExpect(jsonPath("$.lineCount", is(2)))
                                        .andExpect(jsonPath("$.transactionCurrency", is("USD")))
                                        .andExpect(jsonPath("$.baseCurrency", is("USD")))
                                        .andExpect(jsonPath("$.createdBy", is("integration-test")));
                }

                @Test
                @DisplayName("should return 422 for unbalanced entry")
                void shouldRejectUnbalancedEntry() throws Exception {
                        CreateJournalEntryRequestDto unbalanced = CreateJournalEntryRequestDto.builder()
                                        .eventId("EVT-UNBAL")
                                        .postedDate(LocalDate.of(2026, 2, 25))
                                        .transactionCurrency("USD")
                                        .createdBy("test-user")
                                        .lines(List.of(
                                                        JournalLineRequestDto.builder()
                                                                        .accountCode("CASH")
                                                                        .amountCents(10000L)
                                                                        .isCredit(false)
                                                                        .build(),
                                                        JournalLineRequestDto.builder()
                                                                        .accountCode("REVENUE")
                                                                        .amountCents(5000L)
                                                                        .isCredit(true)
                                                                        .build()))
                                        .build();

                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(unbalanced)))
                                        .andExpect(status().isUnprocessableEntity())
                                        .andExpect(jsonPath("$.type", is("/problems/unbalanced-entry")));
                }

                @Test
                @DisplayName("should return 422 for inactive account reference")
                void shouldRejectInactiveAccount() throws Exception {
                        CreateJournalEntryRequestDto withInactive = CreateJournalEntryRequestDto.builder()
                                        .eventId("EVT-INACTIVE")
                                        .postedDate(LocalDate.of(2026, 2, 25))
                                        .transactionCurrency("USD")
                                        .createdBy("test-user")
                                        .lines(List.of(
                                                        JournalLineRequestDto.builder()
                                                                        .accountCode("INACTIVE")
                                                                        .amountCents(10000L)
                                                                        .isCredit(false)
                                                                        .build(),
                                                        JournalLineRequestDto.builder()
                                                                        .accountCode("REVENUE")
                                                                        .amountCents(10000L)
                                                                        .isCredit(true)
                                                                        .build()))
                                        .build();

                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(withInactive)))
                                        .andExpect(status().isUnprocessableEntity())
                                        .andExpect(jsonPath("$.type", is("/problems/inactive-account")));
                }

                @Test
                @DisplayName("should return 400 for missing required fields")
                void shouldReturn400ForMissingFields() throws Exception {
                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("should apply contra-account polarity for balance updates")
                void shouldApplyContraAccountPolarity() throws Exception {
                        String eventId = "EVT-CONTRA-" + UUID.randomUUID().toString().substring(0, 8);
                        CreateJournalEntryRequestDto contraEntry = CreateJournalEntryRequestDto.builder()
                                        .eventId(eventId)
                                        .postedDate(LocalDate.of(2026, 2, 25))
                                        .description("Contra account test")
                                        .transactionCurrency("USD")
                                        .createdBy("integration-test")
                                        .lines(List.of(
                                                        JournalLineRequestDto.builder()
                                                                        .accountCode("EXPENSE")
                                                                        .amountCents(10000L)
                                                                        .isCredit(false)
                                                                        .build(),
                                                        JournalLineRequestDto.builder()
                                                                        .accountCode("ACC_DEP")
                                                                        .amountCents(10000L)
                                                                        .isCredit(true)
                                                                        .build()))
                                        .build();

                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(contraEntry)))
                                        .andExpect(status().isCreated());

                        mockMvc.perform(get("/v1/accounts/{accountCode}", "ACC_DEP")
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.currentBalanceCents", is(10000)));
                }
        }

        // --- GET /v1/journal-entries/{id} ---

        @Nested
        @DisplayName("GET /v1/journal-entries/{id}")
        class GetJournalEntryTests {

                @Test
                @DisplayName("should return 200 with posted journal entry")
                void shouldReturnPostedEntry() throws Exception {
                        String eventId = "EVT-GET-" + UUID.randomUUID().toString().substring(0, 8);

                        // Create an entry first
                        String response = mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(balancedRequest(eventId))))
                                        .andExpect(status().isCreated())
                                        .andReturn().getResponse().getContentAsString();

                        // Extract the ID from response using substring
                        String journalEntryId = jsonMapper.readTree(response)
                                        .get("journalEntryId").asText();

                        // Get by ID
                        mockMvc.perform(get("/v1/journal-entries/{id}", journalEntryId)
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.journalEntryId", is(journalEntryId)))
                                        .andExpect(jsonPath("$.status", is("POSTED")));
                }

                @Test
                @DisplayName("should return 404 for non-existent journal entry")
                void shouldReturn404ForNotFound() throws Exception {
                        mockMvc.perform(get("/v1/journal-entries/{id}", UUID.randomUUID())
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.type", is("/problems/journal-entry-not-found")));
                }
        }

        // --- GET /v1/journal-entries ---

        @Nested
        @DisplayName("GET /v1/journal-entries")
        class ListJournalEntriesTests {

                @Test
                @DisplayName("should return paginated list of journal entries")
                void shouldReturnPaginatedList() throws Exception {
                        // Create two entries
                        String eventId1 = "EVT-LIST1-" + UUID.randomUUID().toString().substring(0, 8);
                        String eventId2 = "EVT-LIST2-" + UUID.randomUUID().toString().substring(0, 8);

                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(balancedRequest(eventId1))))
                                        .andExpect(status().isCreated());

                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(balancedRequest(eventId2))))
                                        .andExpect(status().isCreated());

                        // List all
                        mockMvc.perform(get("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .param("page", "0")
                                        .param("size", "10"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content").isArray());
                }

                @Test
                @DisplayName("should filter journal entries by accountCode")
                void shouldFilterByAccountCode() throws Exception {
                        String revenueEntryEventId = "EVT-REV-" + UUID.randomUUID().toString().substring(0, 8);
                        String expenseEntryEventId = "EVT-EXP-" + UUID.randomUUID().toString().substring(0, 8);

                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(balancedRequest(revenueEntryEventId))))
                                        .andExpect(status().isCreated());

                        mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(expenseRequest(expenseEntryEventId))))
                                        .andExpect(status().isCreated());

                        mockMvc.perform(get("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .param("accountCode", "EXPENSE")
                                        .param("page", "0")
                                        .param("size", "10"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content.length()", is(1)))
                                        .andExpect(jsonPath("$.content[0].description", is("Expense entry")));
                }

                @Test
                @DisplayName("should enforce append-only by blocking update/delete on journal tables")
                void shouldBlockJournalMutations() throws Exception {
                        String eventId = "EVT-IMMUT-" + UUID.randomUUID().toString().substring(0, 8);

                        String response = mockMvc.perform(post("/v1/journal-entries")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(balancedRequest(eventId))))
                                        .andExpect(status().isCreated())
                                        .andReturn().getResponse().getContentAsString();

                        String journalEntryId = jsonMapper.readTree(response).get("journalEntryId").asText();

                        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                                        "UPDATE fis_journal_entry SET description = ? WHERE journal_entry_id = ?",
                                        "mutated", UUID.fromString(journalEntryId)));

                        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                                        "DELETE FROM fis_journal_line WHERE journal_entry_id = ?",
                                        UUID.fromString(journalEntryId)));
                }
        }
}
