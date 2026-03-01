package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("FinancialReportingController Integration Tests")
class FinancialReportingControllerIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private BusinessEntityRepository businessEntityRepository;
        @Autowired
        private AccountRepository accountRepository;
        @Autowired
        private JournalEntryRepository journalEntryRepository;

        private UUID tenantId;
        private static final LocalDate POSTED_DATE = LocalDate.of(2025, 6, 15);

        @BeforeEach
        void setUp() {
                // Create tenant
                BusinessEntity tenant = BusinessEntity.builder()
                                .name("Reporting Test Corp")
                                .baseCurrency("USD")
                                .isActive(true)
                                .build();
                tenantId = businessEntityRepository.save(tenant).getTenantId();

                // Create accounts spanning all five types
                Account cash = createAccount("1001", "Cash", AccountType.ASSET, 0L);
                Account equipment = createAccount("1002", "Equipment", AccountType.ASSET, 0L);
                Account loan = createAccount("2001", "Bank Loan", AccountType.LIABILITY, 0L);
                Account capital = createAccount("3001", "Share Capital", AccountType.EQUITY, 0L);
                Account sales = createAccount("4001", "Sales Revenue", AccountType.REVENUE, 0L);
                Account wages = createAccount("5001", "Wages Expense", AccountType.EXPENSE, 0L);

                // Post a balanced journal entry:
                // DR Cash 10000, CR Capital 10000 (capital injection)
                postBalancedEntry("evt-001", "Capital injection",
                                cash, 10000L, false,
                                capital, 10000L, true);

                // Post another: DR Wages 3000, CR Cash 3000 (pay wages)
                postBalancedEntry("evt-002", "Pay wages",
                                wages, 3000L, false,
                                cash, 3000L, true);

                // Post: DR Cash 5000, CR Sales 5000 (record sale)
                postBalancedEntry("evt-003", "Record sale",
                                cash, 5000L, false,
                                sales, 5000L, true);
        }

        // ─── Trial Balance ────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/trial-balance should return balanced report")
        void trialBalance() throws Exception {
                mockMvc.perform(get("/v1/reports/trial-balance")
                                .header("X-Tenant-Id", tenantId)
                                .param("asOfDate", POSTED_DATE.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.balanced", is(true)))
                                .andExpect(jsonPath("$.metadata.reportType", is("TRIAL_BALANCE")));
        }

        // ─── Balance Sheet ────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/balance-sheet should return structured report")
        void balanceSheet() throws Exception {
                mockMvc.perform(get("/v1/reports/balance-sheet")
                                .header("X-Tenant-Id", tenantId)
                                .param("asOfDate", POSTED_DATE.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.metadata.reportType", is("BALANCE_SHEET")))
                                .andExpect(jsonPath("$.assets").exists())
                                .andExpect(jsonPath("$.liabilities").exists())
                                .andExpect(jsonPath("$.equity").exists())
                                .andExpect(jsonPath("$.totalAssets").isNumber())
                                .andExpect(jsonPath("$.totalLiabilitiesAndEquity").isNumber());
        }

        // ─── Income Statement ─────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/income-statement should compute net income")
        void incomeStatement() throws Exception {
                mockMvc.perform(get("/v1/reports/income-statement")
                                .header("X-Tenant-Id", tenantId)
                                .param("fromDate", "2025-01-01")
                                .param("toDate", "2025-12-31"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.metadata.reportType", is("INCOME_STATEMENT")))
                                .andExpect(jsonPath("$.totalRevenue").isNumber())
                                .andExpect(jsonPath("$.totalExpenses").isNumber())
                                .andExpect(jsonPath("$.netIncome").isNumber());
        }

        // ─── General Ledger ───────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/general-ledger/{code} should return entries with running balance")
        void generalLedger() throws Exception {
                mockMvc.perform(get("/v1/reports/general-ledger/1001")
                                .header("X-Tenant-Id", tenantId)
                                .param("fromDate", "2025-01-01")
                                .param("toDate", "2025-12-31"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountCode", is("1001")))
                                .andExpect(jsonPath("$.entries").isArray())
                                .andExpect(jsonPath("$.closingBalance").isNumber());
        }

        // ─── Account Activity ─────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/account-activity/{code} should return activity summary")
        void accountActivity() throws Exception {
                mockMvc.perform(get("/v1/reports/account-activity/1001")
                                .header("X-Tenant-Id", tenantId)
                                .param("fromDate", "2025-01-01")
                                .param("toDate", "2025-12-31"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountCode", is("1001")))
                                .andExpect(jsonPath("$.totalDebits").isNumber())
                                .andExpect(jsonPath("$.totalCredits").isNumber())
                                .andExpect(jsonPath("$.closingBalance").isNumber());
        }

        // ─── Journal Register ─────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/journal-register should return paginated entries")
        void journalRegister() throws Exception {
                mockMvc.perform(get("/v1/reports/journal-register")
                                .header("X-Tenant-Id", tenantId)
                                .param("fromDate", "2025-01-01")
                                .param("toDate", "2025-12-31"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.entries").isArray())
                                .andExpect(jsonPath("$.totalEntries").isNumber());
        }

        // ─── Cash Flow ────────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/cash-flow should return classified activities")
        void cashFlow() throws Exception {
                mockMvc.perform(get("/v1/reports/cash-flow")
                                .header("X-Tenant-Id", tenantId)
                                .param("fromDate", "2025-01-01")
                                .param("toDate", "2025-12-31"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.operatingActivities").exists())
                                .andExpect(jsonPath("$.investingActivities").exists())
                                .andExpect(jsonPath("$.financingActivities").exists());
        }

        // ─── Aging ────────────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/aging should return age buckets")
        void aging() throws Exception {
                mockMvc.perform(get("/v1/reports/aging")
                                .header("X-Tenant-Id", tenantId)
                                .param("accountType", "ASSET")
                                .param("asOfDate", POSTED_DATE.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountType", is("ASSET")))
                                .andExpect(jsonPath("$.buckets").isArray());
        }

        // ─── Dimension Summary ────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/dimension-summary should work with no matching dimensions")
        void dimensionSummaryEmpty() throws Exception {
                mockMvc.perform(get("/v1/reports/dimension-summary")
                                .header("X-Tenant-Id", tenantId)
                                .param("dimensionKey", "costCenter")
                                .param("fromDate", "2025-01-01")
                                .param("toDate", "2025-12-31"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.dimensionKey", is("costCenter")))
                                .andExpect(jsonPath("$.lines", hasSize(0)));
        }

        // ─── FX Exposure ──────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /v1/reports/fx-exposure should return empty when no FX positions")
        void fxExposureEmpty() throws Exception {
                mockMvc.perform(get("/v1/reports/fx-exposure")
                                .header("X-Tenant-Id", tenantId)
                                .param("asOfDate", POSTED_DATE.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.baseCurrency", is("USD")))
                                .andExpect(jsonPath("$.exposures", hasSize(0)));
        }

        // ─── Error cases ──────────────────────────────────────────────────────

        @Test
        @DisplayName("should return 400 when required date param is missing")
        void missingDateParam() throws Exception {
                mockMvc.perform(get("/v1/reports/trial-balance")
                                .header("X-Tenant-Id", tenantId))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 for non-existent account in general ledger")
        void nonExistentAccount() throws Exception {
                mockMvc.perform(get("/v1/reports/general-ledger/NONEXISTENT")
                                .header("X-Tenant-Id", tenantId)
                                .param("fromDate", "2025-01-01")
                                .param("toDate", "2025-12-31"))
                                .andExpect(status().isNotFound());
        }

        // ─── Test helpers ─────────────────────────────────────────────────────

        private Account createAccount(String code, String name, AccountType type, long balance) {
                return accountRepository.save(Account.builder()
                                .tenantId(tenantId)
                                .code(code)
                                .name(name)
                                .accountType(type)
                                .currencyCode("USD")
                                .currentBalance(balance)
                                .build());
        }

        private void postBalancedEntry(String eventId, String description,
                        Account debitAccount, long debitAmount, boolean debitIsCredit,
                        Account creditAccount, long creditAmount, boolean creditIsCredit) {
                UUID entryId = UUID.randomUUID();
                JournalEntry entry = JournalEntry.builder()
                                .id(entryId)
                                .tenantId(tenantId)
                                .eventId(eventId)
                                .postedDate(POSTED_DATE)
                                .effectiveDate(POSTED_DATE)
                                .transactionDate(POSTED_DATE)
                                .description(description)
                                .status(JournalStatus.POSTED)
                                .transactionCurrency("USD")
                                .baseCurrency("USD")
                                .exchangeRate(BigDecimal.ONE)
                                .createdBy("test-admin")
                                .previousHash("GENESIS")
                                .hash("hash-" + eventId)
                                .fiscalYear(2025)
                                .sequenceNumber(System.nanoTime())
                                .build();

                entry.addLine(JournalLine.builder()
                                .account(debitAccount)
                                .amount(debitAmount)
                                .baseAmount(debitAmount)
                                .isCredit(debitIsCredit)
                                .build());

                entry.addLine(JournalLine.builder()
                                .account(creditAccount)
                                .amount(creditAmount)
                                .baseAmount(creditAmount)
                                .isCredit(creditIsCredit)
                                .build());

                journalEntryRepository.save(entry);
        }
}
