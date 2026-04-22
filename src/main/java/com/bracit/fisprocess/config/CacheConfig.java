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
 * <li><b>accounts</b>: Chart of Accounts entries (1 hour TTL)</li>
 * <li><b>exchangeRates</b>: Daily FX conversion rates (15 minute TTL)</li>
 * <li><b>mappingRules</b>: Event-to-journal mapping rules (2 hour TTL)</li>
 * <li><b>accountingPeriods</b>: Accounting period data (30 min TTL)</li>
 * <li><b>tenants</b>: Tenant configuration (1 hour TTL)</li>
 * <li><b>reports</b>: Report generation results (30 sec TTL)</li>
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

    public static final String CACHE_ACCOUNTS = "accounts";
    public static final String CACHE_EXCHANGE_RATES = "exchangeRates";
    public static final String CACHE_MAPPING_RULES = "mappingRules";
    public static final String CACHE_ACCOUNTING_PERIODS = "accountingPeriods";
    public static final String CACHE_TENANTS = "tenants";
    public static final String CACHE_REPORTS = "reports";

    /**
     * Creates a Redis-backed cache manager with JSON serialization and per-cache TTLs.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues()
                .prefixCacheNameWith("fis:cache:");

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put(CACHE_ACCOUNTS, defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put(CACHE_EXCHANGE_RATES, defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put(CACHE_MAPPING_RULES, defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put(CACHE_ACCOUNTING_PERIODS, defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put(CACHE_TENANTS, defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put(CACHE_REPORTS, defaultConfig.entryTtl(Duration.ofSeconds(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
