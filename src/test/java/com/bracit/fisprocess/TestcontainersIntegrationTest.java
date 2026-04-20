package com.bracit.fisprocess;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Full integration test base using Testcontainers for PostgreSQL, Redis, and RabbitMQ.
 * Use this ONLY for tests that explicitly need real Docker containers:
 * - Flyway migration tests
 * - PostgreSQL-specific EXPLAIN plan tests
 * - Concurrency/locking behavior tests
 * - RabbitMQ message routing tests
 *
 * All other integration tests should extend {@link H2IntegrationTest} instead.
 */
public abstract class TestcontainersIntegrationTest extends AbstractIntegrationTest {
    // Preserves the existing Testcontainers behavior from AbstractIntegrationTest
    // This class exists to make the Docker dependency explicit in test class hierarchy
}
