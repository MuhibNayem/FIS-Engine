package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BatchJournalRepository {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private static final String COPY_JOURNAL_ENTRY_SQL = """
            COPY fis_journal_entry (
                journal_entry_id, tenant_id, event_id, posted_date, effective_date,
                transaction_date, description, reference_id, status, reversal_of_id,
                transaction_currency, base_currency, exchange_rate, created_by,
                created_at, previous_hash, hash, fiscal_year, sequence_number, auto_reverse
            ) FROM STDIN WITH (FORMAT csv, DELIMITER ',', NULL 'NULL')
            """;

    private static final String COPY_JOURNAL_LINE_SQL = """
            COPY fis_journal_line (
                line_id, journal_entry_id, account_id, amount, base_amount,
                is_credit, dimensions, created_at
            ) FROM STDIN WITH (FORMAT csv, DELIMITER ',', NULL 'NULL')
            """;

    private static final String INSERT_JOURNAL_ENTRY_SQL = """
            INSERT INTO fis_journal_entry (
                journal_entry_id, tenant_id, event_id, posted_date, effective_date,
                transaction_date, description, reference_id, status, reversal_of_id,
                transaction_currency, base_currency, exchange_rate, created_by,
                created_at, previous_hash, hash, fiscal_year, sequence_number, auto_reverse
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_JOURNAL_LINE_SQL = """
            INSERT INTO fis_journal_line (
                line_id, journal_entry_id, account_id, amount, base_amount,
                is_credit, dimensions, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Transactional
    public void batchInsertEntries(List<JournalEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);

        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement entryStmt = conn.prepareStatement(INSERT_JOURNAL_ENTRY_SQL);
                 PreparedStatement lineStmt = conn.prepareStatement(INSERT_JOURNAL_LINE_SQL)) {

                for (JournalEntry entry : entries) {
                    setEntryParameters(entryStmt, entry);
                    entryStmt.addBatch();

                    for (JournalLine line : entry.getLines()) {
                        setLineParameters(lineStmt, line);
                        lineStmt.addBatch();
                    }
                }

                int[] entryCounts = entryStmt.executeBatch();
                int[] lineCounts = lineStmt.executeBatch();

                conn.commit();

                meterRegistry.counter("fis.batch.entries.persisted").increment(entries.size());
                meterRegistry.counter("fis.batch.lines.persisted")
                        .increment(entries.stream().mapToInt(e -> e.getLines().size()).sum());

                log.debug("Batch inserted {} entries with {} lines",
                        entryCounts.length, lineCounts.length);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Batch insert failed for {} entries", entries.size(), e);
            meterRegistry.counter("fis.batch.insert.error").increment();
            throw new RuntimeException("Batch insert failed", e);
        } finally {
            sample.stop(Timer.builder("fis.batch.insert.duration").register(meterRegistry));
        }
    }

    @Transactional
    public void copyFromEntries(List<JournalEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);

        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            conn.setAutoCommit(false);

            StringBuilder entryData = new StringBuilder();
            StringBuilder lineData = new StringBuilder();

            for (JournalEntry entry : entries) {
                entryData.append(formatEntryCsv(entry)).append("\n");
                for (JournalLine line : entry.getLines()) {
                    lineData.append(formatLineCsv(line)).append("\n");
                }
            }

            try (PreparedStatement entryCopyStmt = conn.prepareStatement(COPY_JOURNAL_ENTRY_SQL);
                 PreparedStatement lineCopyStmt = conn.prepareStatement(COPY_JOURNAL_LINE_SQL)) {

                byte[] entryBytes = entryData.toString().getBytes();
                byte[] lineBytes = lineData.toString().getBytes();

                entryCopyStmt.setBinaryStream(1, new ByteArrayInputStream(entryBytes), entryBytes.length);
                lineCopyStmt.setBinaryStream(1, new ByteArrayInputStream(lineBytes), lineBytes.length);

                int entryCount = entryCopyStmt.executeUpdate();
                int lineCount = lineCopyStmt.executeUpdate();

                conn.commit();

                meterRegistry.counter("fis.batch.entries.copied").increment(entryCount);
                meterRegistry.counter("fis.batch.lines.copied").increment(lineCount);

                log.debug("COPY inserted {} entries with {} lines", entryCount, lineCount);
            }
        } catch (Exception e) {
            log.error("COPY batch insert failed for {} entries", entries.size(), e);
            meterRegistry.counter("fis.batch.copy.error").increment();
            throw new RuntimeException("COPY batch insert failed", e);
        } finally {
            sample.stop(Timer.builder("fis.batch.copy.duration").register(meterRegistry));
        }
    }

    private void setEntryParameters(PreparedStatement stmt, JournalEntry entry) throws Exception {
        stmt.setObject(1, entry.getId());
        stmt.setObject(2, entry.getTenantId());
        stmt.setString(3, entry.getEventId());
        stmt.setDate(4, java.sql.Date.valueOf(entry.getPostedDate()));
        stmt.setDate(5, java.sql.Date.valueOf(entry.getEffectiveDate()));
        stmt.setDate(6, java.sql.Date.valueOf(entry.getTransactionDate()));
        stmt.setString(7, entry.getDescription());
        stmt.setString(8, entry.getReferenceId());
        stmt.setString(9, entry.getStatus().name());
        stmt.setObject(10, entry.getReversalOfId());
        stmt.setString(11, entry.getTransactionCurrency());
        stmt.setString(12, entry.getBaseCurrency());
        stmt.setBigDecimal(13, entry.getExchangeRate());
        stmt.setString(14, entry.getCreatedBy());
        stmt.setTimestamp(15, Timestamp.from(entry.getCreatedAt().toInstant()));
        stmt.setString(16, entry.getPreviousHash());
        stmt.setString(17, entry.getHash());
        stmt.setInt(18, entry.getFiscalYear());
        stmt.setLong(19, entry.getSequenceNumber());
        stmt.setBoolean(20, entry.isAutoReverse());
    }

    private void setLineParameters(PreparedStatement stmt, JournalLine line) throws Exception {
        stmt.setObject(1, line.getId());
        stmt.setObject(2, line.getJournalEntry().getId());
        stmt.setObject(3, line.getAccount().getAccountId());
        stmt.setLong(4, line.getAmount());
        stmt.setLong(5, line.getBaseAmount());
        stmt.setBoolean(6, line.isCredit());
        stmt.setString(7, line.getDimensions() != null ? toJson(line.getDimensions()) : "NULL");
        stmt.setTimestamp(8, Timestamp.from(line.getCreatedAt().toInstant()));
    }

    private String formatEntryCsv(JournalEntry entry) {
        return String.join(",",
                escapeCsv(entry.getId().toString()),
                escapeCsv(entry.getTenantId().toString()),
                escapeCsv(entry.getEventId()),
                escapeCsv(entry.getPostedDate().toString()),
                escapeCsv(entry.getEffectiveDate().toString()),
                escapeCsv(entry.getTransactionDate().toString()),
                escapeCsv(entry.getDescription()),
                escapeCsv(entry.getReferenceId()),
                escapeCsv(entry.getStatus().name()),
                entry.getReversalOfId() != null ? escapeCsv(entry.getReversalOfId().toString()) : "NULL",
                escapeCsv(entry.getTransactionCurrency()),
                escapeCsv(entry.getBaseCurrency()),
                entry.getExchangeRate().toString(),
                escapeCsv(entry.getCreatedBy()),
                entry.getCreatedAt().toInstant().toString(),
                escapeCsv(entry.getPreviousHash()),
                escapeCsv(entry.getHash()),
                String.valueOf(entry.getFiscalYear()),
                String.valueOf(entry.getSequenceNumber()),
                String.valueOf(entry.isAutoReverse())
        );
    }

    private String formatLineCsv(JournalLine line) {
        return String.join(",",
                escapeCsv(line.getId().toString()),
                escapeCsv(line.getJournalEntry().getId().toString()),
                escapeCsv(line.getAccount().getAccountId().toString()),
                String.valueOf(line.getAmount()),
                String.valueOf(line.getBaseAmount()),
                String.valueOf(line.isCredit()),
                line.getDimensions() != null ? escapeCsv(toJson(line.getDimensions())) : "NULL",
                line.getCreatedAt().toInstant().toString()
        );
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "NULL";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\\")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "NULL";
        }
        return map.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}