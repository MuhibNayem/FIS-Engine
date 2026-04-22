package com.bracit.fisprocess.service;

import com.bracit.fisprocess.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmingService {

    private final CacheManager cacheManager;
    private final AtomicBoolean warmingInProgress = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmCachesOnStartup() {
        if (warmingInProgress.compareAndSet(false, true)) {
            log.info("Starting cache warming on application startup");
            try {
                warmCaches();
            } finally {
                warmingInProgress.set(false);
            }
        }
    }

    public void warmCaches() {
        log.info("Cache warming started");

        warmCache(CacheConfig.CACHE_ACCOUNTS);
        warmCache(CacheConfig.CACHE_EXCHANGE_RATES);
        warmCache(CacheConfig.CACHE_ACCOUNTING_PERIODS);
        warmCache(CacheConfig.CACHE_TENANTS);

        log.info("Cache warming completed");
    }

    public void warmCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            log.debug("Warming cache: {}", cacheName);
            cache.clear();
        }
    }

    public void evictCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            log.info("Evicting cache: {}", cacheName);
            cache.clear();
        }
    }

    public void evictAllCaches() {
        cacheManager.getCacheNames().forEach(this::evictCache);
    }

    public boolean isWarmingInProgress() {
        return warmingInProgress.get();
    }
}