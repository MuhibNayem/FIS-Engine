package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Accounting Period & Currency Integration Tests")
class AccountingPeriodCurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BusinessEntityRepository businessEntityRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JsonMapper jsonMapper;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        BusinessEntity tenant = BusinessEntity.builder()
                .name("Phase 4 Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .build();
        tenantId = businessEntityRepository.save(tenant).getTenantId();

        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("CASH-USD")
                .name("Cash USD")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("REV-USD")
                .name("Revenue USD")
                .accountType(AccountType.REVENUE)
                .currencyCode("USD")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("CASH-EUR")
                .name("Cash EUR")
                .accountType(AccountType.ASSET)
                .currencyCode("EUR")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("REV-EUR")
                .name("Revenue EUR")
                .accountType(AccountType.REVENUE)
                .currencyCode("EUR")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("FX_REVAL_RESERVE")
                .name("FX Revaluation Reserve")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("FX_UNREALIZED_GAIN")
                .name("FX Unrealized Gain")
                .accountType(AccountType.REVENUE)
                .currencyCode("USD")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("FX_UNREALIZED_LOSS")
                .name("FX Unrealized Loss")
                .accountType(AccountType.EXPENSE)
                .currencyCode("USD")
                .build());
    }

    @Test
    void shouldRejectOverlappingAccountingPeriods() throws Exception {
        mockMvc.perform(post("/v1/accounting-periods")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"2026-02","startDate":"2026-02-01","endDate":"2026-02-28"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/accounting-periods")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"2026-02-overlap","startDate":"2026-02-15","endDate":"2026-03-15"}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/overlapping-accounting-period"));
    }

    @Test
    void shouldEnforceSequentialHardCloseAndStrictCascadingReopen() throws Exception {
        String jan = createPeriod("2026-01", "2026-01-01", "2026-01-31");
        String feb = createPeriod("2026-02", "2026-02-01", "2026-02-28");

        transition(feb, "SOFT_CLOSED", 200);
        transition(feb, "HARD_CLOSED", 422);

        transition(jan, "SOFT_CLOSED", 200);
        transition(jan, "HARD_CLOSED", 200);
        transition(feb, "HARD_CLOSED", 200);

        transition(jan, "OPEN", 422);
        transition(feb, "OPEN", 200);
        transition(jan, "OPEN", 200);
    }

    @Test
    void shouldEnforceClosedPeriodRulesAndAdminOverrideForSoftClose() throws Exception {
        String periodId = createPeriod("2026-02", "2026-02-01", "2026-02-28");
        transition(periodId, "SOFT_CLOSED", 200);

        mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(journalRequest("EVT-SOFT-BLOCK", "USD", "CASH-USD", "REV-USD"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/period-closed"));

        mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .header("X-Actor-Role", "FIS_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(journalRequest("EVT-SOFT-ADMIN", "USD", "CASH-USD", "REV-USD"))))
                .andExpect(status().isCreated());

        transition(periodId, "HARD_CLOSED", 200);
        mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .header("X-Actor-Role", "FIS_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(journalRequest("EVT-HARD-BLOCK", "USD", "CASH-USD", "REV-USD"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/period-closed"));

        mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(CreateJournalEntryRequestDto.builder()
                        .eventId("EVT-NOPERIOD")
                        .postedDate(LocalDate.of(2026, 3, 1))
                        .transactionCurrency("USD")
                        .createdBy("phase4-test")
                        .lines(List.of(
                                JournalLineRequestDto.builder().accountCode("CASH-USD").amountCents(10_000L).isCredit(false).build(),
                                JournalLineRequestDto.builder().accountCode("REV-USD").amountCents(10_000L).isCredit(true).build()))
                        .build())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/accounting-period-not-found"));
    }

    @Test
    void shouldUploadAndApplyExchangeRateWithPriorDateFallback() throws Exception {
        createPeriod("2026-02", "2026-02-01", "2026-02-28");
        mockMvc.perform(post("/v1/exchange-rates")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rates":[{"sourceCurrency":"EUR","targetCurrency":"USD","rate":1.2,"effectiveDate":"2026-02-24"}]}
                        """))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(journalRequest("EVT-EUR-FX", "EUR", "CASH-EUR", "REV-EUR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchangeRate").value(1.2))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = jsonMapper.readTree(body);
        String jeId = response.get("journalEntryId").asText();
        List<Long> baseAmounts = jdbcTemplate.queryForList(
                "SELECT base_amount FROM fis_journal_line WHERE journal_entry_id = ? ORDER BY base_amount ASC",
                Long.class, UUID.fromString(jeId));
        assertThat(baseAmounts).containsExactly(12_000L, 12_000L);
    }

    @Test
    void shouldRejectMissingExchangeRateAndCurrencyMismatch() throws Exception {
        createPeriod("2026-02", "2026-02-01", "2026-02-28");

        mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(journalRequest("EVT-MISS-FX", "EUR", "CASH-EUR", "REV-EUR"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/exchange-rate-not-found"));

        mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(journalRequest("EVT-CURR-MISMATCH", "USD", "CASH-EUR", "REV-EUR"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/account-currency-mismatch"));
    }

    @Test
    void shouldRunFunctionalCurrencyTranslationAndPostCtaJournal() throws Exception {
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("CTA-OCI")
                .name("CTA OCI")
                .accountType(AccountType.EQUITY)
                .currencyCode("USD")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("TRANS-RESERVE")
                .name("Translation Reserve")
                .accountType(AccountType.EQUITY)
                .currencyCode("USD")
                .build());

        String periodId = createPeriod("2026-02", "2026-02-01", "2026-02-28");
        mockMvc.perform(post("/v1/exchange-rates")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rates":[
                          {"sourceCurrency":"EUR","targetCurrency":"USD","rate":1.5,"effectiveDate":"2026-02-10"},
                          {"sourceCurrency":"EUR","targetCurrency":"USD","rate":1.1,"effectiveDate":"2026-02-25"}
                        ]}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(journalRequest("EVT-TRANS-SOURCE", "EUR", "CASH-EUR", "REV-EUR"))))
                .andExpect(status().isCreated());

        transition(periodId, "SOFT_CLOSED", 200);

        mockMvc.perform(post("/v1/revaluations/periods/{periodId}/translation", periodId)
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId":"EVT-TRANS-RUN",
                          "createdBy":"phase4-test",
                          "ctaOciAccountCode":"CTA-OCI",
                          "translationReserveAccountCode":"TRANS-RESERVE"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.generatedJournalEntryIds", hasSize(1)));
    }

    private String createPeriod(String name, String startDate, String endDate) throws Exception {
        String response = mockMvc.perform(post("/v1/accounting-periods")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"startDate\":\"" + startDate + "\",\"endDate\":\"" + endDate + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("periodId").asText();
    }

    private void transition(String periodId, String targetStatus, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/v1/accounting-periods/{periodId}/status", periodId)
                .header("X-Tenant-Id", tenantId)
                .header("X-Actor-Id", "phase4-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + targetStatus + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private CreateJournalEntryRequestDto journalRequest(String eventId, String txCurrency, String debitAccount,
            String creditAccount) {
        return CreateJournalEntryRequestDto.builder()
                .eventId(eventId)
                .postedDate(LocalDate.of(2026, 2, 25))
                .transactionCurrency(txCurrency)
                .createdBy("phase4-test")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode(debitAccount).amountCents(10_000L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode(creditAccount).amountCents(10_000L).isCredit(true).build()))
                .build();
    }
}
