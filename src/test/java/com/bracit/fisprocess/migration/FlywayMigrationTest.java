package com.bracit.fisprocess.migration;

import com.bracit.fisprocess.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test verifying that Flyway migrations
 * execute cleanly against a real PostgreSQL instance via Testcontainers.
 * <p>
 * If this test passes, the schema is valid and Hibernate's
 * {@code ddl-auto=validate} will succeed against these migrations.
 */
@SpringBootTest
@DisplayName("Flyway Migration Integration Tests")
class FlywayMigrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("All Flyway migrations execute successfully and Hibernate validates schema")
    void flywayMigrationsRunSuccessfully() {
        // If the Spring context loads without error, Flyway migrations ran
        // and Hibernate's validate mode confirmed the entity-schema match.
    }
}
