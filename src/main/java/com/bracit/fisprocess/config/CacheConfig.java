package com.bracit.fisprocess.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis-based cache configuration for FIS-Engine.
 * <p>
 * Provides distributed caching for frequently accessed, rarely-changing data:
 * <ul>
 * <li><b>Accounts</b>: Chart of Accounts entries (1 hour TTL)</li>
 * <li><b>Exchange Rates</b>: Daily FX conversion rates (15 minute TTL)</li>
 * <li><b>Mapping Rules</b>: Event-to-journal mapping rules (2 hour TTL)</li>
 * </ul>
 * <p>
 * Redis is used instead of local caching (Caffeine) for these entities because:
 * <ul>
 * <li>Multi-instance deployments need shared cache state</li>
 * <li>Data is tenant-scoped and must be consistent across all nodes</li>
 * <li>Entities are serializable to JSON (unlike compiled SpEL expressions)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Creates a Redis-backed cache manager with JSON serialization and per-cache TTLs.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration: 30 min TTL, JSON serialization
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues()
                .prefixCacheNameWith("fis:cache:");

        // Per-cache custom TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("accounts",
                defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("exchangeRates",
                defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("mappingRules",
                defaultConfig.entryTtl(Duration.ofHours(2)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
