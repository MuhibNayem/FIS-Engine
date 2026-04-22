package com.bracit.fisprocess.service;

import com.bracit.fisprocess.service.Shard;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionException;

@Component
@Slf4j
public class ShardAwareExecutorService {

    private final Map<Shard, ThreadPoolExecutor> shardExecutors = new EnumMap<>(Shard.class);
    private final Map<Shard, CircuitBreaker> shardCircuitBreakers = new EnumMap<>(Shard.class);
    private final MeterRegistry meterRegistry;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${fis.worker.core-pool-size:10}")
    private int corePoolSize;

    @Value("${fis.worker.max-pool-size:50}")
    private int maxPoolSize;

    @Value("${fis.worker.queue-capacity:1000}")
    private int queueCapacity;

    @Value("${fis.worker.keep-alive-seconds:60}")
    private long keepAliveSeconds;

    @Value("${fis.worker.enabled:true}")
    private boolean enabled;

    @Value("${fis.circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;

    @Value("${fis.circuit-breaker.wait-duration-open-seconds:15}")
    private int waitDurationOpenSeconds;

    @Value("${fis.circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;

    public ShardAwareExecutorService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void initialize() {
        if (!enabled) {
            log.info("ShardAwareExecutorService disabled");
            return;
        }

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationOpenSeconds))
                .slidingWindowSize(slidingWindowSize)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);

        for (Shard shard : Shard.values()) {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    corePoolSize,
                    maxPoolSize,
                    keepAliveSeconds,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueCapacity),
                    r -> {
                        Thread t = new Thread(r, "shard-worker-" + shard.name() + "-" + System.nanoTime());
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

            shardExecutors.put(shard, executor);

            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("shard-" + shard.name());
            shardCircuitBreakers.put(shard, circuitBreaker);

            meterRegistry.gauge("fis.worker.pool.size", Tags.of("shard", shard.name()),
                    executor, e -> e.getPoolSize());
            meterRegistry.gauge("fis.worker.active.count", Tags.of("shard", shard.name()),
                    executor, e -> e.getActiveCount());
            meterRegistry.gauge("fis.worker.queue.depth", Tags.of("shard", shard.name()),
                    executor, e -> e.getQueue().size());
            meterRegistry.gauge("fis.worker.queue.remaining", Tags.of("shard", shard.name()),
                    executor, e -> e.getQueue().remainingCapacity());
            meterRegistry.gauge("fis.circuit.breaker.state", Tags.of("shard", shard.name()),
                    circuitBreaker, cb -> cb.getState().ordinal());

            log.info("Initialized executor and circuit breaker for shard {} with core={}, max={}, queue={}",
                    shard.name(), corePoolSize, maxPoolSize, queueCapacity);
        }
    }

    public <T> T executeWithShardIsolation(Shard shard, Callable<T> task) throws Exception {
        CircuitBreaker circuitBreaker = shardCircuitBreakers.get(shard);
        if (circuitBreaker == null) {
            throw new IllegalArgumentException("No circuit breaker for shard: " + shard);
        }

        return CircuitBreaker.decorateCallable(circuitBreaker, task).call();
    }

    public void executeToShardWithIsolation(Shard shard, Runnable task) {
        CircuitBreaker circuitBreaker = shardCircuitBreakers.get(shard);
        if (circuitBreaker == null) {
            throw new IllegalArgumentException("No circuit breaker for shard: " + shard);
        }

        try {
            CircuitBreaker.decorateRunnable(circuitBreaker, task).run();
        } catch (Exception e) {
            log.error("Error executing task on shard {}: {}", shard, e.getMessage());
            meterRegistry.counter("fis.shard.execution.error", "shard", shard.name()).increment();
            throw new RuntimeException("Shard execution failed: " + shard, e);
        }
    }

    public Future<?> submitToShard(Shard shard, Runnable task) {
        ThreadPoolExecutor executor = shardExecutors.get(shard);
        if (executor == null) {
            throw new IllegalArgumentException("No executor for shard: " + shard);
        }
        return executor.submit(task);
    }

    public <T> Future<T> submitToShard(Shard shard, Callable<T> task) {
        ThreadPoolExecutor executor = shardExecutors.get(shard);
        if (executor == null) {
            throw new IllegalArgumentException("No executor for shard: " + shard);
        }
        return executor.submit(task);
    }

    public void executeToShard(Shard shard, Runnable task) {
        ThreadPoolExecutor executor = shardExecutors.get(shard);
        if (executor == null) {
            throw new IllegalArgumentException("No executor for shard: " + shard);
        }
        executor.execute(task);
    }

    public int getActiveCountForShard(Shard shard) {
        ThreadPoolExecutor executor = shardExecutors.get(shard);
        return executor != null ? executor.getActiveCount() : 0;
    }

    public int getQueueDepthForShard(Shard shard) {
        ThreadPoolExecutor executor = shardExecutors.get(shard);
        return executor != null ? executor.getQueue().size() : 0;
    }

    public int getTotalQueueDepth() {
        return shardExecutors.values().stream()
                .mapToInt(e -> e.getQueue().size())
                .sum();
    }

    public boolean isOverloaded(Shard shard, int threshold) {
        CircuitBreaker circuitBreaker = shardCircuitBreakers.get(shard);
        if (circuitBreaker != null && circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            return true;
        }

        ThreadPoolExecutor executor = shardExecutors.get(shard);
        if (executor == null) {
            return false;
        }
        int queueDepth = executor.getQueue().size();
        int activeCount = executor.getActiveCount();
        return queueDepth > threshold || activeCount >= maxPoolSize;
    }

    public CircuitBreaker.State getCircuitBreakerState(Shard shard) {
        CircuitBreaker circuitBreaker = shardCircuitBreakers.get(shard);
        return circuitBreaker != null ? circuitBreaker.getState() : CircuitBreaker.State.CLOSED;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down shard executors");
        for (Map.Entry<Shard, ThreadPoolExecutor> entry : shardExecutors.entrySet()) {
            ThreadPoolExecutor executor = entry.getValue();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Shutdown executor for shard {}", entry.getKey().name());
        }
    }
}