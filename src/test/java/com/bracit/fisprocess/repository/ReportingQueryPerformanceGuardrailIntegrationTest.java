package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Reporting Query Performance Guardrail Integration Tests")
class ReportingQueryPerformanceGuardrailIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID journalEntryId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO fis_business_entity (tenant_id, name, base_currency, is_active, created_at, updated_at)
                VALUES (?, ?, 'USD', TRUE, ?, ?)
                """, tenantId, "Perf Guardrail Tenant", OffsetDateTime.now(), OffsetDateTime.now());

        jdbcTemplate.update("""
                INSERT INTO fis_account (
                    account_id, tenant_id, code, name, account_type, currency_code,
                    current_balance, is_active, is_contra, created_at, updated_at
                )
                VALUES (?, ?, 'CASH', 'Cash', 'ASSET', 'USD', 10000, TRUE, FALSE, ?, ?)
                """, accountId, tenantId, OffsetDateTime.now(), OffsetDateTime.now());

        jdbcTemplate.update("""
                INSERT INTO fis_journal_entry (
                    journal_entry_id, tenant_id, event_id, posted_date, effective_date, transaction_date,
                    description, reference_id, status, reversal_of_id, transaction_currency, base_currency,
                    exchange_rate, created_by, created_at, previous_hash, hash, fiscal_year, sequence_number, auto_reverse
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'POSTED', NULL, 'USD', 'USD', 1.0, ?, ?, ?, ?, ?, ?, FALSE)
                """,
                journalEntryId, tenantId, "PERF-GL-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.of(2026, 2, 25), LocalDate.of(2026, 2, 25), LocalDate.of(2026, 2, 25),
                "perf guardrail seed", "PERF-REF-1", "perf-test",
                OffsetDateTime.now(), "seed-prev-hash", "seed-hash", 2026, 1L);

        jdbcTemplate.update("""
                INSERT INTO fis_journal_line (line_id, journal_entry_id, account_id, amount, base_amount, is_credit, dimensions, created_at)
                VALUES (?, ?, ?, 10000, 10000, FALSE, NULL, ?)
                """, UUID.randomUUID(), journalEntryId, accountId, OffsetDateTime.now());
    }

    @Test
    @DisplayName("required reporting indexes should exist after migrations")
    void requiredReportingIndexesShouldExist() {
        assertIndexExists("idx_je_tenant_status_effective_date");
        assertIndexExists("idx_je_tenant_status_effective_seq");
        assertIndexExists("idx_jl_account_entry");
        assertIndexExists("idx_account_tenant_code_active");
        assertIndexExists("idx_account_tenant_type_active");
    }

    @Test
    @DisplayName("journal register count explain plan should avoid sequential scan on fis_journal_entry")
    void journalRegisterCountPlanShouldAvoidJeSeqScan() throws Exception {
        String plan = explain("""
                SELECT COUNT(*)
                FROM fis_journal_entry je
                WHERE je.tenant_id = ?
                  AND je.effective_date BETWEEN ? AND ?
                  AND je.status IN ('POSTED', 'CORRECTION', 'REVERSED')
                """, stmt -> {
            stmt.setObject(1, tenantId);
            stmt.setObject(2, LocalDate.of(2026, 2, 1));
            stmt.setObject(3, LocalDate.of(2026, 2, 28));
        });

        assertThat(plan).doesNotContain("Seq Scan on fis_journal_entry");
        assertThat(plan).contains("idx_je_tenant_status_effective");
    }

    private void assertIndexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = ?
                """, Integer.class, indexName);
        assertThat(count).isEqualTo(1);
    }

    private String explain(String sql, SqlBinder binder) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET LOCAL enable_seqscan = off");
                }
                try (PreparedStatement statement = connection.prepareStatement("EXPLAIN " + sql)) {
                    binder.bind(statement);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<String> rows = new ArrayList<>();
                        while (resultSet.next()) {
                            rows.add(resultSet.getString(1));
                        }
                        return String.join("\n", rows);
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws Exception;
    }
}
