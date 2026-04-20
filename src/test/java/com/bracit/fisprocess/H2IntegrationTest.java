package com.bracit.fisprocess;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Lightweight integration test base using H2 in-memory database.
 * Use this for tests that need Spring context but do NOT require
 * real PostgreSQL, Redis, or RabbitMQ.
 *
 * Tests that extend this class run WITHOUT Docker containers,
 * making them fast and suitable for parallel execution.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:fis_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
public abstract class H2IntegrationTest {
    // Inherits @SpringBootTest context from subclasses
}
