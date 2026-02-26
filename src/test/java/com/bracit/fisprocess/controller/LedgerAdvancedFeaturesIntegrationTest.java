package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.ExchangeRate;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.ExchangeRateRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ledger Advanced Features Integration Tests")
class LedgerAdvancedFeaturesIntegrationTest extends AbstractIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JournalEntryRepository journalEntryRepository;
    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private UUID tenantId;
    private UUID periodId;

    @BeforeEach
    void setUp() {
        BusinessEntity tenant = businessEntityRepository.save(BusinessEntity.builder()
                .name("Phase 5 Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .build());
        tenantId = tenant.getTenantId();

        periodId = accountingPeriodRepository.save(AccountingPeriod.builder()
                .tenantId(tenantId)
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .status(PeriodStatus.OPEN)
                .build()).getPeriodId();

        saveAccount("CASH_USD", AccountType.ASSET, "USD");
        saveAccount("REV_USD", AccountType.REVENUE, "USD");
        saveAccount("CASH_EUR", AccountType.ASSET, "EUR");
        saveAccount("REV_EUR", AccountType.REVENUE, "EUR");
        saveAccount("FX_REVAL_RESERVE", AccountType.ASSET, "USD");
        saveAccount("FX_UNREALIZED_GAIN", AccountType.REVENUE, "USD");
        saveAccount("FX_UNREALIZED_LOSS", AccountType.EXPENSE, "USD");

        exchangeRateRepository.save(ExchangeRate.builder()
                .tenantId(tenantId)
                .sourceCurrency("EUR")
                .targetCurrency("USD")
                .rate(new java.math.BigDecimal("1.10"))
                .effectiveDate(LocalDate.of(2026, 2, 1))
                .createdAt(OffsetDateTime.now())
                .build());
    }

    @Test
    void shouldReverseAndCorrectWithIdempotencyAndRejectDoubleReversal() throws Exception {
        UUID original = createJournalEntry("EVT-P5-ORIG", "USD", "CASH_USD", "REV_USD", 20_000L);

        String reverseBody = jsonMapper.writeValueAsString(Map.of(
                "eventId", "EVT-P5-REV-1",
                "reason", "duplicate posting",
                "createdBy", "phase5-test"));

        mockMvc.perform(post("/v1/journal-entries/{id}/reverse", original)
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reverseBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalJournalEntryId").value(original.toString()));

        mockMvc.perform(post("/v1/journal-entries/{id}/reverse", original)
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of(
                        "eventId", "EVT-P5-REV-2",
                        "reason", "second try",
                        "createdBy", "phase5-test"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/invalid-reversal"));

        UUID toCorrect = createJournalEntry("EVT-P5-CORR-ORIG", "USD", "CASH_USD", "REV_USD", 10_000L);
        String correctionBody = jsonMapper.writeValueAsString(Map.of(
                "eventId", "EVT-P5-CORR-NEW",
                "reversalEventId", "EVT-P5-CORR-REV",
                "postedDate", "2026-02-25",
                "description", "corrected posting",
                "referenceId", "CORR-1",
                "transactionCurrency", "USD",
                "createdBy", "phase5-test",
                "lines", List.of(
                        Map.of("accountCode", "CASH_USD", "amountCents", 8_000, "isCredit", false),
                        Map.of("accountCode", "REV_USD", "amountCents", 8_000, "isCredit", true))));

        mockMvc.perform(post("/v1/journal-entries/{id}/correct", toCorrect)
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionBody))
                .andExpect(status().isCreated());

        long correctionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fis_journal_entry WHERE reversal_of_id = ?",
                Long.class,
                toCorrect);
        assertThat(correctionRows).isEqualTo(1L);

        long beforeDuplicate = journalEntryRepository.count();
        mockMvc.perform(post("/v1/journal-entries/{id}/correct", toCorrect)
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionBody))
                .andExpect(status().isCreated());
        assertThat(journalEntryRepository.count()).isEqualTo(beforeDuplicate);
    }

    @Test
    void shouldApplyMappingRuleThroughEventPipelineAndWriteAuditLogs() throws Exception {
        String createRuleBody = """
                {
                  "eventType":"SALARY_DISBURSED",
                  "description":"salary mapping",
                  "createdBy":"admin@fis",
                  "lines":[
                    {"accountCodeExpression":"CASH_USD","isCredit":true,"amountExpression":"${payload.amountCents}","sortOrder":2},
                    {"accountCodeExpression":"REV_USD","isCredit":false,"amountExpression":"${payload.amountCents}","sortOrder":1}
                  ]
                }
                """;

        String createResponse = mockMvc.perform(post("/v1/mapping-rules")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRuleBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        UUID ruleId = UUID.fromString(jsonMapper.readTree(createResponse).get("ruleId").asText());

        mockMvc.perform(put("/v1/mapping-rules/{id}", ruleId)
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventType":"SALARY_DISBURSED",
                          "description":"salary mapping updated",
                          "updatedBy":"admin@fis",
                          "lines":[
                            {"accountCodeExpression":"REV_USD","isCredit":false,"amountExpression":"${payload.amountCents}","sortOrder":1},
                            {"accountCodeExpression":"CASH_USD","isCredit":true,"amountExpression":"${payload.amountCents}","sortOrder":2}
                          ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                .eventId("EVT-P5-MAP-1")
                .eventType("SALARY_DISBURSED")
                .occurredAt(OffsetDateTime.now())
                .postedDate(LocalDate.of(2026, 2, 25))
                .description("salary payout")
                .referenceId("PAY-1")
                .transactionCurrency("USD")
                .createdBy("payroll")
                .payload(Map.of("amountCents", 15000))
                .build();

        mockMvc.perform(post("/v1/events")
                .header("X-Tenant-Id", tenantId)
                .header("X-Source-System", "PAYROLL")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(event)))
                .andExpect(status().isAccepted());

        waitForCondition(() -> journalEntryRepository.existsByTenantIdAndEventId(tenantId, "EVT-P5-MAP-1"), 12_000L);

        mockMvc.perform(delete("/v1/mapping-rules/{id}", ruleId)
                .header("X-Tenant-Id", tenantId)
                .header("X-Actor-Id", "admin@fis"))
                .andExpect(status().isNoContent());

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fis_audit_log WHERE tenant_id = ? AND entity_type = 'MAPPING_RULE'",
                Long.class,
                tenantId);
        assertThat(auditCount).isGreaterThanOrEqualTo(3L);
    }

    @Test
    void shouldRunPeriodEndRevaluationIdempotently() throws Exception {
        mockMvc.perform(post("/v1/exchange-rates")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rates":[
                          {"sourceCurrency":"EUR","targetCurrency":"USD","rate":1.2,"effectiveDate":"2026-02-20"},
                          {"sourceCurrency":"EUR","targetCurrency":"USD","rate":1.3,"effectiveDate":"2026-02-28"}
                        ]}
                        """))
                .andExpect(status().isCreated());

        createJournalEntry("EVT-P5-EUR", "EUR", "CASH_EUR", "REV_EUR", 10_000L);

        mockMvc.perform(patch("/v1/accounting-periods/{periodId}/status", periodId)
                .header("X-Tenant-Id", tenantId)
                .header("X-Actor-Id", "phase5-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"status\":\"SOFT_CLOSED\"}"))
                .andExpect(status().isOk());

        String runBody = """
                {
                  "eventId":"EVT-P5-REVAL",
                  "createdBy":"phase5-test",
                  "reserveAccountCode":"FX_REVAL_RESERVE",
                  "gainAccountCode":"FX_UNREALIZED_GAIN",
                  "lossAccountCode":"FX_UNREALIZED_LOSS"
                }
                """;

        String first = mockMvc.perform(post("/v1/revaluations/periods/{periodId}", periodId)
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(runBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode firstJson = jsonMapper.readTree(first);
        assertThat(firstJson.get("generatedJournalEntryIds").size()).isGreaterThanOrEqualTo(1);

        String second = mockMvc.perform(post("/v1/revaluations/periods/{periodId}", periodId)
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(runBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode secondJson = jsonMapper.readTree(second);
        assertThat(secondJson.get("runId").asText()).isEqualTo(firstJson.get("runId").asText());
    }

    @Test
    void shouldPostRealizedFxGainFromSettlement() throws Exception {
        UUID original = createJournalEntry("EVT-P5-SETTLE-ORIG", "EUR", "CASH_EUR", "REV_EUR", 10_000L);

        mockMvc.perform(post("/v1/settlements")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"EVT-P5-SETTLE-1",
                                  "originalJournalEntryId":"%s",
                                  "settlementDate":"2026-02-28",
                                  "settlementRate":1.30,
                                  "monetaryAccountCode":"CASH_EUR",
                                  "gainAccountCode":"FX_UNREALIZED_GAIN",
                                  "lossAccountCode":"FX_UNREALIZED_LOSS",
                                  "createdBy":"phase5-test"
                                }
                                """.formatted(original)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.realizedGainLossJournalEntryId").isNotEmpty())
                .andExpect(jsonPath("$.realizedDeltaBaseCents").value(2000));
    }

    @Test
    void shouldPersistTraceparentFromRestToOutbox() throws Exception {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        CreateJournalEntryRequestDto request = CreateJournalEntryRequestDto.builder()
                .eventId("EVT-P5-TRACE")
                .postedDate(LocalDate.of(2026, 2, 25))
                .transactionCurrency("USD")
                .createdBy("phase5-test")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH_USD").amountCents(1000L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REV_USD").amountCents(1000L).isCredit(true).build()))
                .build();

        mockMvc.perform(post("/v1/journal-entries")
                        .header("X-Tenant-Id", tenantId)
                        .header("traceparent", traceparent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        String stored = jdbcTemplate.queryForObject(
                "SELECT traceparent FROM fis_outbox WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                tenantId);
        assertThat(stored).isEqualTo(traceparent);
    }

    private UUID createJournalEntry(
            String eventId, String transactionCurrency, String debitAccount, String creditAccount, long amount) throws Exception {
        CreateJournalEntryRequestDto request = CreateJournalEntryRequestDto.builder()
                .eventId(eventId)
                .postedDate(LocalDate.of(2026, 2, 20))
                .transactionCurrency(transactionCurrency)
                .createdBy("phase5-test")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode(debitAccount).amountCents(amount).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode(creditAccount).amountCents(amount).isCredit(true).build()))
                .build();

        String response = mockMvc.perform(post("/v1/journal-entries")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(jsonMapper.readTree(response).get("journalEntryId").asText());
    }

    private void saveAccount(String code, AccountType type, String currency) {
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code(code)
                .name(code)
                .accountType(type)
                .currencyCode(currency)
                .build());
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
