package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.exception.AccountNotFoundException;
import com.bracit.fisprocess.exception.ReportParameterException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.dto.response.AccountActivityReportDto;
import com.bracit.fisprocess.dto.response.AgingReportDto;
import com.bracit.fisprocess.dto.response.BalanceSheetReportDto;
import com.bracit.fisprocess.dto.response.CashFlowReportDto;
import com.bracit.fisprocess.dto.response.DimensionSummaryReportDto;
import com.bracit.fisprocess.dto.response.FxExposureReportDto;
import com.bracit.fisprocess.dto.response.GeneralLedgerReportDto;
import com.bracit.fisprocess.dto.response.IncomeStatementReportDto;
import com.bracit.fisprocess.dto.response.JournalRegisterReportDto;
import com.bracit.fisprocess.dto.response.TrialBalanceReportDto;
import com.bracit.fisprocess.repository.ReportingRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.impl.ReportingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportingServiceImpl Unit Tests")
class ReportingServiceImplTest {

        @Mock
        private ReportingRepository reportingRepository;

        @Mock
        private BusinessEntityRepository businessEntityRepository;

        @InjectMocks
        private ReportingServiceImpl service;

        private static final UUID TENANT_ID = UUID.randomUUID();
        private static final String BASE_CURRENCY = "USD";
        private static final LocalDate AS_OF = LocalDate.of(2025, 12, 31);
        private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
        private static final LocalDate TO = LocalDate.of(2025, 12, 31);

        @BeforeEach
        void setUp() {
                BusinessEntity tenant = BusinessEntity.builder()
                                .tenantId(TENANT_ID)
                                .name("Test Corp")
                                .baseCurrency(BASE_CURRENCY)
                                .isActive(true)
                                .build();
                org.mockito.Mockito.lenient()
                                .when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                                .thenReturn(Optional.of(tenant));
        }

        // ─── Trial Balance ────────────────────────────────────────────────────

        @Nested
        @DisplayName("Trial Balance")
        class TrialBalanceTests {

                @Test
                @DisplayName("should compute correct totals and balanced flag when debits equal credits")
                void balancedTrialBalance() {
                        when(reportingRepository.findTrialBalanceLines(TENANT_ID, AS_OF))
                                        .thenReturn(List.of(
                                                        Map.of("account_code", "1001", "account_name", "Cash",
                                                                        "account_type", "ASSET", "total_debits", 5000L,
                                                                        "total_credits", 0L),
                                                        Map.of("account_code", "2001", "account_name", "Loan",
                                                                        "account_type", "LIABILITY", "total_debits", 0L,
                                                                        "total_credits", 5000L)));

                        TrialBalanceReportDto result = service.generateTrialBalance(TENANT_ID, AS_OF);

                        assertThat(result.getTotalDebits()).isEqualTo(5000L);
                        assertThat(result.getTotalCredits()).isEqualTo(5000L);
                        assertThat(result.isBalanced()).isTrue();
                        assertThat(result.getLines()).hasSize(2);
                        assertThat(result.getMetadata().getReportType()).isEqualTo("TRIAL_BALANCE");
                }

                @Test
                @DisplayName("should flag unbalanced when debits do not equal credits")
                void unbalancedTrialBalance() {
                        when(reportingRepository.findTrialBalanceLines(TENANT_ID, AS_OF))
                                        .thenReturn(List.of(
                                                        Map.of("account_code", "1001", "account_name", "Cash",
                                                                        "account_type", "ASSET", "total_debits", 5000L,
                                                                        "total_credits", 0L),
                                                        Map.of("account_code", "2001", "account_name", "Loan",
                                                                        "account_type", "LIABILITY", "total_debits", 0L,
                                                                        "total_credits", 3000L)));

                        TrialBalanceReportDto result = service.generateTrialBalance(TENANT_ID, AS_OF);

                        assertThat(result.isBalanced()).isFalse();
                        assertThat(result.getTotalDebits()).isNotEqualTo(result.getTotalCredits());
                }
        }

        // ─── Balance Sheet ────────────────────────────────────────────────────

        @Nested
        @DisplayName("Balance Sheet")
        class BalanceSheetTests {

                @Test
                @DisplayName("should verify Assets = Liabilities + Equity")
                void balancedBalanceSheet() {
                        when(reportingRepository.findBalanceSheetAccounts(TENANT_ID, AS_OF))
                                        .thenReturn(List.of(
                                                        Map.of("account_code", "1001", "account_name", "Cash",
                                                                        "account_type", "ASSET", "net_balance", 10000L),
                                                        Map.of("account_code", "2001", "account_name", "Loan",
                                                                        "account_type", "LIABILITY", "net_balance",
                                                                        -7000L),
                                                        Map.of("account_code", "3001", "account_name", "Capital",
                                                                        "account_type", "EQUITY", "net_balance",
                                                                        -3000L)));

                        BalanceSheetReportDto result = service.generateBalanceSheet(TENANT_ID, AS_OF);

                        assertThat(result.getTotalAssets()).isEqualTo(10000L);
                        assertThat(result.getTotalLiabilitiesAndEquity()).isEqualTo(10000L);
                        assertThat(result.isBalanced()).isTrue();
                        assertThat(result.getAssets().getLines()).hasSize(1);
                        assertThat(result.getLiabilities().getLines()).hasSize(1);
                        assertThat(result.getEquity().getLines()).hasSize(1);
                }
        }

        // ─── Income Statement ─────────────────────────────────────────────────

        @Nested
        @DisplayName("Income Statement")
        class IncomeStatementTests {

                @Test
                @DisplayName("should compute net income = revenue - expenses")
                void netIncomeCalculation() {
                        when(reportingRepository.findIncomeStatementAccounts(TENANT_ID, FROM, TO))
                                        .thenReturn(List.of(
                                                        Map.of("account_code", "4001", "account_name", "Sales",
                                                                        "account_type", "REVENUE", "net_amount",
                                                                        -8000L),
                                                        Map.of("account_code", "5001", "account_name", "Wages",
                                                                        "account_type", "EXPENSE", "net_amount",
                                                                        3000L)));

                        IncomeStatementReportDto result = service.generateIncomeStatement(TENANT_ID, FROM, TO);

                        assertThat(result.getTotalRevenue()).isEqualTo(8000L);
                        assertThat(result.getTotalExpenses()).isEqualTo(3000L);
                        assertThat(result.getNetIncome()).isEqualTo(5000L);
                }

                @Test
                @DisplayName("should reject invalid date range")
                void invalidDateRange() {
                        assertThatThrownBy(() -> service.generateIncomeStatement(TENANT_ID, TO, FROM))
                                        .isInstanceOf(ReportParameterException.class)
                                        .hasMessageContaining("fromDate must not be after toDate");
                }
        }

        // ─── General Ledger ───────────────────────────────────────────────────

        @Nested
        @DisplayName("General Ledger")
        class GeneralLedgerTests {

                @Test
                @DisplayName("should compute running balance correctly")
                void runningBalance() {
                        when(reportingRepository.accountExists(TENANT_ID, "1001")).thenReturn(true);
                        when(reportingRepository.computeOpeningBalance(TENANT_ID, "1001", FROM)).thenReturn(1000L);
                        when(reportingRepository.findGeneralLedgerEntries(TENANT_ID, "1001", FROM, TO))
                                        .thenReturn(List.of(
                                                        Map.of("journal_entry_id", UUID.randomUUID(), "sequence_number",
                                                                        1L,
                                                                        "posted_date", java.sql.Date.valueOf(FROM),
                                                                        "description", "Deposit",
                                                                        "debit_amount", 500L, "credit_amount", 0L),
                                                        Map.of("journal_entry_id", UUID.randomUUID(), "sequence_number",
                                                                        2L,
                                                                        "posted_date",
                                                                        java.sql.Date.valueOf(FROM.plusDays(1)),
                                                                        "description", "Withdrawal",
                                                                        "debit_amount", 0L, "credit_amount", 200L)));

                        GeneralLedgerReportDto result = service.generateGeneralLedger(TENANT_ID, "1001", FROM, TO);

                        assertThat(result.getOpeningBalance()).isEqualTo(1000L);
                        assertThat(result.getClosingBalance()).isEqualTo(1300L); // 1000 + 500 - 200
                        assertThat(result.getEntries()).hasSize(2);
                        assertThat(result.getEntries().get(0).getRunningBalance()).isEqualTo(1500L); // 1000 + 500
                        assertThat(result.getEntries().get(1).getRunningBalance()).isEqualTo(1300L); // 1500 - 200
                }

                @Test
                @DisplayName("should throw when account does not exist")
                void accountNotFound() {
                        when(reportingRepository.accountExists(TENANT_ID, "NONEXISTENT")).thenReturn(false);

                        assertThatThrownBy(() -> service.generateGeneralLedger(TENANT_ID, "NONEXISTENT", FROM, TO))
                                        .isInstanceOf(AccountNotFoundException.class);
                }
        }

        // ─── Cash Flow ────────────────────────────────────────────────────────

        @Nested
        @DisplayName("Cash Flow")
        class CashFlowTests {

                @Test
                @DisplayName("should classify movements by activity type")
                void classifyMovements() {
                        when(reportingRepository.findCashBalance(TENANT_ID, FROM.minusDays(1))).thenReturn(5000L);
                        when(reportingRepository.findNetMovementByAccountType(TENANT_ID, FROM, TO))
                                        .thenReturn(List.of(
                                                        Map.of("account_type", "REVENUE", "account_code", "4001",
                                                                        "account_name", "Sales", "net_movement",
                                                                        -8000L),
                                                        Map.of("account_type", "EXPENSE", "account_code", "5001",
                                                                        "account_name", "Wages", "net_movement", 3000L),
                                                        Map.of("account_type", "ASSET", "account_code", "1002",
                                                                        "account_name", "Equipment", "net_movement",
                                                                        2000L),
                                                        Map.of("account_type", "LIABILITY", "account_code", "2001",
                                                                        "account_name", "Loan", "net_movement",
                                                                        1000L)));

                        CashFlowReportDto result = service.generateCashFlow(TENANT_ID, FROM, TO);

                        assertThat(result.getOperatingActivities().getLines()).hasSize(2);
                        assertThat(result.getInvestingActivities().getLines()).hasSize(1);
                        assertThat(result.getFinancingActivities().getLines()).hasSize(1);
                        assertThat(result.getOpeningCash()).isEqualTo(5000L);
                }
        }

        // ─── Account Activity ─────────────────────────────────────────────────

        @Nested
        @DisplayName("Account Activity")
        class AccountActivityTests {

                @Test
                @DisplayName("should compute closing balance from opening + debits - credits")
                void closingBalance() {
                        when(reportingRepository.accountExists(TENANT_ID, "1001")).thenReturn(true);
                        when(reportingRepository.computeOpeningBalance(TENANT_ID, "1001", FROM)).thenReturn(1000L);
                        when(reportingRepository.findAccountActivity(TENANT_ID, "1001", FROM, TO))
                                        .thenReturn(Map.of("total_debits", 5000L, "total_credits", 2000L,
                                                        "transaction_count", 10L));

                        AccountActivityReportDto result = service.generateAccountActivity(TENANT_ID, "1001", FROM, TO);

                        assertThat(result.getOpeningBalance()).isEqualTo(1000L);
                        assertThat(result.getTotalDebits()).isEqualTo(5000L);
                        assertThat(result.getTotalCredits()).isEqualTo(2000L);
                        assertThat(result.getClosingBalance()).isEqualTo(4000L); // 1000 + 5000 - 2000
                        assertThat(result.getTransactionCount()).isEqualTo(10L);
                }
        }

        // ─── Journal Register ─────────────────────────────────────────────────

        @Nested
        @DisplayName("Journal Register")
        class JournalRegisterTests {

                @Test
                @DisplayName("should return paginated journal entries with totals")
                void paginatedRegister() {
                        when(reportingRepository.countJournalRegister(TENANT_ID, FROM, TO)).thenReturn(55L);
                        when(reportingRepository.findJournalRegister(eq(TENANT_ID), eq(FROM), eq(TO), eq(0), eq(50)))
                                        .thenReturn(List.of(
                                                        Map.of("journal_entry_id", UUID.randomUUID(), "sequence_number",
                                                                        1L,
                                                                        "posted_date", java.sql.Date.valueOf(FROM),
                                                                        "description", "Init",
                                                                        "status", "POSTED", "created_by", "admin",
                                                                        "total_debits", 1000L, "total_credits",
                                                                        1000L)));

                        JournalRegisterReportDto result = service.generateJournalRegister(TENANT_ID, FROM, TO, 0, 50);

                        assertThat(result.getTotalEntries()).isEqualTo(55L);
                        assertThat(result.getTotalPages()).isEqualTo(2L);
                        assertThat(result.getEntries()).hasSize(1);
                }

                @Test
                @DisplayName("should reject invalid page size")
                void invalidPageSize() {
                        assertThatThrownBy(() -> service.generateJournalRegister(TENANT_ID, FROM, TO, 0, 0))
                                        .isInstanceOf(ReportParameterException.class)
                                        .hasMessageContaining("Page size");
                }
        }

        // ─── Dimension Summary ────────────────────────────────────────────────

        @Nested
        @DisplayName("Dimension Summary")
        class DimensionSummaryTests {

                @Test
                @DisplayName("should aggregate by dimension key")
                void aggregateByDimension() {
                        when(reportingRepository.findDimensionSummary(TENANT_ID, "costCenter", FROM, TO))
                                        .thenReturn(List.of(
                                                        Map.of("dimension_value", "CC-100", "debit_total", 3000L,
                                                                        "credit_total", 1000L, "net_amount", 2000L),
                                                        Map.of("dimension_value", "CC-200", "debit_total", 2000L,
                                                                        "credit_total", 500L, "net_amount", 1500L)));

                        DimensionSummaryReportDto result = service.generateDimensionSummary(TENANT_ID, "costCenter",
                                        FROM, TO);

                        assertThat(result.getDimensionKey()).isEqualTo("costCenter");
                        assertThat(result.getLines()).hasSize(2);
                        assertThat(result.getGrandTotal()).isEqualTo(3500L);
                }

                @Test
                @DisplayName("should reject blank dimension key")
                void blankDimensionKey() {
                        assertThatThrownBy(() -> service.generateDimensionSummary(TENANT_ID, "", FROM, TO))
                                        .isInstanceOf(ReportParameterException.class);
                }
        }

        // ─── FX Exposure ──────────────────────────────────────────────────────

        @Nested
        @DisplayName("FX Exposure")
        class FxExposureTests {

                @Test
                @DisplayName("should group asset/liability exposure by currency")
                void groupByCurrency() {
                        when(reportingRepository.findFxExposure(TENANT_ID, AS_OF))
                                        .thenReturn(List.of(
                                                        Map.of("currency", "EUR", "account_type", "ASSET",
                                                                        "net_balance", 5000L),
                                                        Map.of("currency", "EUR", "account_type", "LIABILITY",
                                                                        "net_balance", -2000L)));
                        when(reportingRepository.findLatestRate(TENANT_ID, "EUR", BASE_CURRENCY))
                                        .thenReturn(BigDecimal.valueOf(1.1));

                        FxExposureReportDto result = service.generateFxExposure(TENANT_ID, AS_OF);

                        assertThat(result.getExposures()).hasSize(1);
                        assertThat(result.getExposures().getFirst().getCurrency()).isEqualTo("EUR");
                        assertThat(result.getExposures().getFirst().getAssetExposure()).isEqualTo(5000L);
                        assertThat(result.getExposures().getFirst().getLiabilityExposure()).isEqualTo(-2000L);
                        assertThat(result.getExposures().getFirst().getNetExposure()).isEqualTo(3000L);
                }
        }

        // ─── Aging ────────────────────────────────────────────────────────────

        @Nested
        @DisplayName("Aging Analysis")
        class AgingTests {

                @Test
                @DisplayName("should return bucketed aging data")
                void agingBuckets() {
                        when(reportingRepository.findAgingBuckets(TENANT_ID, "ASSET", AS_OF))
                                        .thenReturn(List.of(
                                                        Map.of("bucket_label", "0-30 days", "amount_cents", 5000L,
                                                                        "entry_count", 10L),
                                                        Map.of("bucket_label", "31-60 days", "amount_cents", 3000L,
                                                                        "entry_count", 5L),
                                                        Map.of("bucket_label", "61-90 days", "amount_cents", 1000L,
                                                                        "entry_count", 2L)));

                        AgingReportDto result = service.generateAging(TENANT_ID, "ASSET", AS_OF);

                        assertThat(result.getBuckets()).hasSize(3);
                        assertThat(result.getGrandTotal()).isEqualTo(9000L);
                        assertThat(result.getAccountType()).isEqualTo("ASSET");
                }

                @Test
                @DisplayName("should reject invalid account type")
                void invalidAccountType() {
                        assertThatThrownBy(() -> service.generateAging(TENANT_ID, "REVENUE", AS_OF))
                                        .isInstanceOf(ReportParameterException.class)
                                        .hasMessageContaining("ASSET or LIABILITY");
                }
        }

        // ─── Tenant validation ────────────────────────────────────────────────

        @Test
        @DisplayName("should throw when tenant not found")
        void tenantNotFound() {
                UUID unknownTenant = UUID.randomUUID();
                when(businessEntityRepository.findByTenantIdAndIsActiveTrue(unknownTenant))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.generateTrialBalance(unknownTenant, AS_OF))
                                .isInstanceOf(TenantNotFoundException.class);
        }
}
