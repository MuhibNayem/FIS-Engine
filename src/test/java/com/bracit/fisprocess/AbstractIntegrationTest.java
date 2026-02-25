package com.bracit.fisprocess;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Shared base class for integration tests that require a real PostgreSQL
 * instance.
 * <p>
 * Manages a single shared {@link PostgreSQLContainer} via Testcontainers and
 * injects
 * the datasource properties into the Spring context. Subclasses should be
 * annotated
 * with {@code @SpringBootTest} (or a slice annotation) and will automatically
 * get
 * a PostgreSQL-backed datasource with Flyway migrations applied.
 * <p>
 * Also provisions a Redis container so Redis-backed configuration can be tested
 * without relying on local services.
 */
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fis_test")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @SuppressWarnings("resource")
    private static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        // Start once per JVM test process to avoid stale Spring context connection
        // data when containers are restarted between test classes.
        redis.start();
        postgres.start();
        rabbit.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        // Silence OTLP exporter noise in tests.
        registry.add("management.otlp.metrics.export.enabled", () -> "false");
    }
}
