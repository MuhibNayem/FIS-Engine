package com.bracit.fisprocess.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class AppInstanceHealthIndicator {

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    public AppInstanceHealthIndicator(
            StringRedisTemplate redisTemplate,
            @Value("${spring.application.name:fis-process}") String appName,
            @Value("${server.port:8080}") int serverPort) {
        this.redisTemplate = redisTemplate;
        this.instanceId = appName + ":" + serverPort + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);

        try {
            String pong = redisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());

            result.put("status", "UP");
            result.put("redis", "PONG".equals(pong) ? "connected" : "unavailable");
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
            log.error("Instance {} health check failed", instanceId, e);
        }

        return result;
    }
}