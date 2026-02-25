package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Converts fis_journal_line.dimensions from JSON to JSONB for PostgreSQL.
 * No-op for non-PostgreSQL databases (e.g., H2 in local/test profiles).
 */
public class V8__convert_journal_line_dimensions_to_jsonb extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!isPostgreSql(connection)) {
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    ALTER TABLE fis_journal_line
                    ALTER COLUMN dimensions
                    TYPE jsonb
                    USING dimensions::jsonb
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_jl_dimensions_gin
                    ON fis_journal_line
                    USING GIN (dimensions)
                    """);
        }
    }

    private boolean isPostgreSql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        return "PostgreSQL".equalsIgnoreCase(productName);
    }
}
