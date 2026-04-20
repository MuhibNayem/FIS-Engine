package com.bracit.fisprocess;

import org.junit.jupiter.api.Test;

/**
 * Verifies the Spring ApplicationContext loads successfully.
 * Uses H2 in-memory database for fast startup - does not require Docker.
 */
class FisProcessApplicationTests extends H2IntegrationTest {

    @Test
    void contextLoads() {
        // If this passes, the Spring context loaded without errors.
        // All auto-configurations, beans, and Flyway migrations succeeded.
    }
}
