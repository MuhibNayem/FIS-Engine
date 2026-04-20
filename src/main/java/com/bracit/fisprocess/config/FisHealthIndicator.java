package com.bracit.fisprocess.config;

import com.bracit.fisprocess.repository.OutboxEventRepository;
import com.bracit.fisprocess.service.DeadLetterQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Comprehensive health indicator that checks all critical dependencies:
 * PostgreSQL, Redis, RabbitMQ, and the outbox backlog.
 * <p>
 * Returns detailed health status for each dependency to aid operations
 * and debugging. Exposed via {@code /actuator/health}.
 */
@Component
@RequiredArgsConstructor
public class FisHealthIndicator {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterQueueService deadLetterQueueService;

    /** Threshold above which the outbox health status is considered DOWN. */
    private static final long DOWN_BACKLOG_THRESHOLD = 1000;
    /** Threshold above which the outbox health status is considered WARNING (still UP). */
    private static final long WARN_BACKLOG_THRESHOLD = 500;
    /** Age in seconds above which the outbox health is DOWN regardless of backlog. */
    private static final long MAX_OLDEST_AGE_SECONDS = 600; // 10 minutes

    /**
     * Checks all dependencies and returns a health status map.
     * This is exposed via the /actuator/health endpoint.
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("database", checkDatabase());
        details.put("redis", checkRedis());
        details.put("rabbitmq", checkRabbitMQ());
        details.put("outbox", checkOutbox());
        return details;
    }

    /**
     * Checks PostgreSQL connectivity with a simple SELECT 1 query.
     */
    private Map<String, Object> checkDatabase() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Integer queryResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (queryResult != null && queryResult == 1) {
                result.put("status", "UP");
                result.put("query", "SELECT 1");
            } else {
                result.put("status", "DOWN");
                result.put("detail", "SELECT 1 returned unexpected result");
            }
        } catch (Exception ex) {
            result.put("status", "DOWN");
            result.put("error", "Database connectivity check failed");
        }
        return result;
    }

    /**
     * Checks Redis connectivity with a PING command.
     */
    private Map<String, Object> checkRedis() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String pong = redisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());
            if ("PONG".equals(pong)) {
                result.put("status", "UP");
                result.put("ping", "PONG");
            } else {
                result.put("status", "DOWN");
                result.put("detail", "Unexpected response: " + pong);
            }
        } catch (Exception ex) {
            result.put("status", "DOWN");
            result.put("error", "Redis connectivity check failed");
        }
        return result;
    }

    /**
     * Checks RabbitMQ connectivity by retrieving server version.
     */
    private Map<String, Object> checkRabbitMQ() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String version = rabbitTemplate.execute(channel ->
                    channel.getConnection().getServerProperties().get("version").toString());
            result.put("status", "UP");
            result.put("version", version);
        } catch (Exception ex) {
            result.put("status", "DOWN");
            result.put("error", "RabbitMQ connectivity check failed");
        }
        return result;
    }

    /**
     * Checks the health of the outbox system.
     * <p>
     * Status logic:
     * <ul>
     *   <li>DOWN if unpublished &gt; 1000 or oldest event &gt; 10 minutes</li>
     *   <li>UP (with warning details) if unpublished &gt; 500</li>
     *   <li>UP otherwise</li>
     * </ul>
     */
    private Map<String, Object> checkOutbox() {
        Map<String, Object> result = new LinkedHashMap<>();

        long unpublishedCount = outboxEventRepository.countByPublishedFalseAndDlqFalse();
        long dlqSize = deadLetterQueueService.dlqSize();
        long oldestAgeSeconds = 0;

        var oldestOpt = outboxEventRepository.findOldestUnpublishedCreatedAt();
        if (oldestOpt.isPresent()) {
            oldestAgeSeconds = Duration.between(oldestOpt.get(), OffsetDateTime.now()).getSeconds();
        }

        result.put("unpublishedCount", unpublishedCount);
        result.put("oldestAgeSeconds", oldestAgeSeconds);
        result.put("dlqSize", dlqSize);

        // Determine health status
        boolean downDueToBacklog = unpublishedCount > DOWN_BACKLOG_THRESHOLD;
        boolean downDueToAge = oldestAgeSeconds > MAX_OLDEST_AGE_SECONDS;

        if (downDueToBacklog || downDueToAge) {
            result.put("status", "DOWN");
            StringBuilder reason = new StringBuilder("Outbox health degraded:");
            if (downDueToBacklog) {
                reason.append(" backlog=").append(unpublishedCount)
                        .append(" (threshold=").append(DOWN_BACKLOG_THRESHOLD).append(")");
            }
            if (downDueToAge) {
                reason.append(" oldestAge=").append(oldestAgeSeconds).append("s")
                        .append(" (max=").append(MAX_OLDEST_AGE_SECONDS).append("s)");
            }
            result.put("detail", reason.toString());
        } else if (unpublishedCount > WARN_BACKLOG_THRESHOLD) {
            result.put("status", "UP");
            result.put("warning", "Backlog approaching critical threshold: count="
                    + unpublishedCount + " (warn=" + WARN_BACKLOG_THRESHOLD
                    + ", critical=" + DOWN_BACKLOG_THRESHOLD + ")");
        } else {
            result.put("status", "UP");
        }

        return result;
    }
}
