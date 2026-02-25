package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Enforces append-only semantics for core ledger tables in PostgreSQL.
 * <p>
 * Blocks UPDATE/DELETE operations on:
 * - fis_journal_entry
 * - fis_journal_line
 * <p>
 * No-op for non-PostgreSQL databases.
 */
public class V9__enforce_append_only_ledger extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!isPostgreSql(connection)) {
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE OR REPLACE FUNCTION fis_block_ledger_mutation()
                    RETURNS trigger
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        RAISE EXCEPTION 'Append-only violation: % on % is not allowed', TG_OP, TG_TABLE_NAME;
                    END;
                    $$;
                    """);

            stmt.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1
                            FROM pg_trigger
                            WHERE tgname = 'trg_fis_journal_entry_block_update'
                        ) THEN
                            CREATE TRIGGER trg_fis_journal_entry_block_update
                            BEFORE UPDATE ON fis_journal_entry
                            FOR EACH ROW
                            EXECUTE FUNCTION fis_block_ledger_mutation();
                        END IF;
                    END $$;
                    """);

            stmt.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1
                            FROM pg_trigger
                            WHERE tgname = 'trg_fis_journal_entry_block_delete'
                        ) THEN
                            CREATE TRIGGER trg_fis_journal_entry_block_delete
                            BEFORE DELETE ON fis_journal_entry
                            FOR EACH ROW
                            EXECUTE FUNCTION fis_block_ledger_mutation();
                        END IF;
                    END $$;
                    """);

            stmt.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1
                            FROM pg_trigger
                            WHERE tgname = 'trg_fis_journal_line_block_update'
                        ) THEN
                            CREATE TRIGGER trg_fis_journal_line_block_update
                            BEFORE UPDATE ON fis_journal_line
                            FOR EACH ROW
                            EXECUTE FUNCTION fis_block_ledger_mutation();
                        END IF;
                    END $$;
                    """);

            stmt.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1
                            FROM pg_trigger
                            WHERE tgname = 'trg_fis_journal_line_block_delete'
                        ) THEN
                            CREATE TRIGGER trg_fis_journal_line_block_delete
                            BEFORE DELETE ON fis_journal_line
                            FOR EACH ROW
                            EXECUTE FUNCTION fis_block_ledger_mutation();
                        END IF;
                    END $$;
                    """);
        }
    }

    private boolean isPostgreSql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        return "PostgreSQL".equalsIgnoreCase(productName);
    }
}
