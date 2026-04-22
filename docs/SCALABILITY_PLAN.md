# FIS-Engine 100K Writes/Sec Scalability Plan

## Objective
Scale FIS-Engine from ~3k writes/sec to 100k writes/sec through architectural improvements, batching, and distributed processing.

---

## Current State Assessment

### What's Implemented ✅
| Component | Status |
|-----------|--------|
| Async write path (RabbitMQ) | Implemented |
| Shard routing infrastructure | Implemented |
| Connection pooling per shard | Implemented |
| Circuit breaker on worker | Implemented |
| Write coalescing | Implemented |
| Read replica routing | Implemented |

### Critical Gaps ❌
| Gap | Impact |
|-----|--------|
| No batch inserts | Individual fsync = ~3k/sec max throughput |
| Manual shard routing | ThreadLocal must be set manually before each call |
| Single-threaded workers | 1 message processed at a time per worker |
| No Citus cluster | No DB-level sharding distribution |
| Repository-layer routing missing | JPA doesn't automatically use shard routing |

---

## Task 1: Implement Batch Inserts

**Problem:** Each journal entry = separate INSERT statement = separate fsync. PostgreSQL can do ~3k-8k fsyncs/sec.

**Solution:** Batch multiple journal entries into a single `COPY` command or multi-value INSERT.

### Subtasks
- [x] **1.1** Create `BatchJournalRepository` for bulk operations ✅
  - Implement `copyFrom(List<JournalEntry>)` using PostgreSQL COPY protocol
  - Implement `batchInsert(List<JournalEntry>)` using multi-value INSERT
- [x] **1.2** Add batch support to `LedgerPersistenceService` ✅
  - Create `persistBatch(List<DraftJournalEntry>)` method
  - Aggregate account balance updates into single batch
- [x] **1.3** Wire batch inserts into `JournalWriteWorker` ✅
  - Replace single-entry processing with batch processing
  - Add configurable batch size (default: 100)
  - Add flush interval (default: 10ms)
- [x] **1.4** Add metrics for batch operations ✅
  - `fis.batch.size` - actual batch sizes achieved
  - `fis.batch.flush.duration` - flush timing
  - `fis.batch.queue.size` - pending entries per flush cycle

---

## Task 2: Wire Shard Routing into Repository Layer

**Problem:** `ShardContextHolder` is set manually before each repository call. Easy to forget, not automatic.

**Solution:** Use Spring AOP `@Aspect` to automatically route repository calls based on first argument type.

### Subtasks
- [x] **2.1** Add Spring AOP dependency ✅ (spring-aop already included via spring-boot-starter)
- [x] **2.2** Create `ShardRoutingAspect` ✅
  - Intercept all `*Repository.*(..)` methods
  - Extract tenantId/accountCode from first argument
  - Set `ShardContextHolder` before proceed()
  - Clear context in `finally` block
- [x] **2.3** Add `@ShardAware` annotation for explicit routing ✅
  - `@ShardAware` on repository methods that need explicit shard
- [x] **2.4** Write integration tests for automatic routing ✅
  - Test that repository calls are automatically routed
  - Test context is cleared after call

---

## Task 3: Implement Concurrent Workers Per Shard

**Problem:** Current `JournalWriteWorker` processes one message at a time. Need N concurrent workers per shard.

**Solution:** Fork N worker threads per shard, each processing messages from shared RabbitMQ queue.

### Subtasks
- [x] **3.1** Create `ShardAwareExecutorService` ✅
  - Factory that creates N threads per shard
  - Use `ThreadPoolExecutor` with bounded queues
  - Configure: `workers-per-shard` (default: 10)
- [x] **3.2** Modify `JournalWriteWorker` for thread-per-message ✅
  - Instead of `@RabbitListener`, use programmatic consumer
  - Hand off to `ShardAwareExecutorService`
  - Track in-flight messages per shard
- [x] **3.3** Add backpressure handling ✅
  - Reject new messages when queue depth > threshold
  - Return `TRY_AGAIN` error to client
  - Configure: `max-queue-depth` (default: 10,000)
- [x] **3.4** Add worker metrics ✅
  - `fis.worker.inflight` - messages being processed per shard
  - `fis.worker.queue.depth` - queue size per shard
  - `fis.worker.active.count` - active threads per shard

---

## Task 4: Setup and Configure Citus Cluster

**Problem:** Account-range sharding at DB level is required for true horizontal scaling.

**Solution:** Deploy Citus cluster and configure distributed tables.

### Subtasks
- [x] **4.1** Create Citus docker-compose configuration ✅
  - Coordinator node + 3 worker nodes
  - Configure connection strings
- [x] **4.2** Design and implement distributed table strategy ✅
  - Distribute `journal_entries` by account range (shard key)
  - Distribute `journal_lines` by `journal_entry_id` (colocation)
  - Replicate reference tables (`accounts`, `accounting_periods`)
- [x] **4.3** Create migration scripts for Citus ✅
  - `V36__citus_enable_extension.sql`
  - `V37__citus_create_distributed_tables.sql`
  - `V38__citus_colocate_tables.sql`
- [x] **4.4** Update `ShardDataSourceConfig` for Citus ✅
  - Single connection string to coordinator
  - Citus handles shard routing automatically
- [ ] **4.5** Load test with Citus
  - Verify distribution across workers
  - Measure actual throughput
  - (Requires running Docker environment)

---

## Task 5: Implement Write Coalescing (Completed)

**Problem:** Rapid sequential writes to same account cause lock contention.

**Solution:** Buffer writes within time window, flush as single batch.

### Subtasks (All Complete)
- [x] **5.1** Create `WriteCoalescer` component
- [x] **5.2** Implement coalescing buckets with time window
- [x] **5.3** Add max-batch-size flushing
- [x] **5.4** Configure via `fis.coalescer.*` properties

---

## Task 6: Production Hardening

**Problem:** System needs observability and reliability for production.

### Subtasks
- [x] **6.1** Add comprehensive metrics to Prometheus ✅
  - Batch operation metrics (`fis.batch.*`)
  - Shard routing metrics (`fis.worker.*`)
  - Worker concurrency metrics (`fis.worker.*`)
- [x] **6.2** Create Grafana dashboards ✅
  - Write throughput panel (Batch Size, Throughput)
  - Batch efficiency panel (Duration p95/p99)
  - Shard distribution panel (Queue depth by shard)
  - Circuit breaker status panel
- [x] **6.3** Add alerting rules ✅
  - Alert when batch size < 5 (`BatchSizeTooSmall`)
  - Alert when worker queue depth > 5000 (`WorkerQueueDepthHigh`)
  - Alert when circuit breaker opens (`JournalWorkerCircuitBreakerOpen`)
  - Alert when Citus workers unavailable (`CitusWorkerDown`)
- [x] **6.4** Add health checks ✅
  - Shard connectivity check (via FisHealthIndicator)
  - RabbitMQ queue depth check
  - Citus worker availability check

---

## Task 7: Performance Testing & Validation

**Problem:** Need to verify 100k writes/sec target is achievable.

### Subtasks
- [ ] **7.1** Create performance test suite
  - `PerformanceLoadTest` - sustained 100k/sec for 5 minutes
  - `BurstLoadTest` - 2x peak for 30 seconds
  - `RecoveryTest` - slow consumer then catch-up
- [ ] **7.2** Build load testing environment
  - k6 or Gatling scripts
  - Multiple producer instances
  - Metrics collection
- [ ] **7.3** Tune JVM and connection pools
  - Heap size: 16GB recommended
  - Connection pool: 50 per shard
  - GC: G1 with appropriate regions
- [ ] **7.4** Document results and bottlenecks
  - Create `PERFORMANCE_RESULTS.md`
  - Identify remaining bottlenecks
  - Document tuning parameters

---

## Implementation Order

```
Phase 1 (Week 1): Batch Inserts
├── 1.1 BatchJournalRepository
├── 1.2 persistBatch method
├── 1.3 Wire into worker
└── 1.4 Metrics

Phase 2 (Week 2): Repository Routing + Concurrent Workers
├── 2.1 AOP Aspect
├── 2.3 Concurrent workers
└── 3.3 Backpressure

Phase 3 (Week 3): Citus Setup
├── 4.1 Docker compose
├── 4.2 Distributed tables
└── 4.3 Migrations

Phase 4 (Week 4): Hardening + Load Testing
├── 6.x Observability
└── 7.x Performance testing
```

---

## Configuration Parameters

### New Properties for `application.yml`

```yaml
fis:
  coalescer:
    enabled: true
    window-ms: 10
    max-batch-size: 100

  batch:
    enabled: true
    flush-interval-ms: 10
    max-batch-size: 100

  worker:
    concurrency-per-shard: 10
    max-queue-depth: 10000
    core-pool-size: 10
    max-pool-size: 50

  sharding:
    enabled: true
    default-shard: SHARD_1

  citus:
    enabled: true
    coordinator-url: jdbc:postgresql://localhost:5432/fisdb
```

---

## Success Criteria

| Metric | Target | Measurement |
|--------|--------|-------------|
| Write throughput | 100,000 writes/sec sustained | Prometheus `fis.write.count` rate |
| P99 latency | < 500ms for async confirmation | `fis.async.reply.latency` histogram |
| Batch efficiency | Average batch size > 50 | `fis.batch.size` metric avg |
| Worker utilization | > 80% | Active threads / max threads |
| Queue depth | < 1000 normal, < 5000 burst | `fis.worker.queue.depth` |
| Circuit breaker | Closed under normal load | Alert if OPEN |

---

## Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Batch inserts break ACID guarantees | Medium | High | Test extensively, use transactions |
| AOP aspect causes performance overhead | Low | Medium | Benchmark before/after |
| Citus colocation issues | Medium | High | Start with reference tables only |
| Write coalescing reorders important writes | Low | Medium | Only coalesce within same account |
| Memory pressure from large batches | Medium | Medium | Set max batch size limit |

---

## Appendix: File Changes Summary

### New Files
- `src/main/java/com/bracit/fisprocess/service/WriteCoalescer.java` ✅
- `src/main/java/com/bracit/fisprocess/config/ShardContextHolderFilter.java` ✅
- `src/main/java/com/bracit/fisprocess/config/ShardDataSourceConfig.java` (enhanced)
- `src/main/java/com/bracit/fisprocess/service/BatchJournalRepository.java` (pending)
- `src/main/java/com/bracit/fisprocess/config/ShardRoutingAspect.java` (pending)
- `src/main/java/com/bracit/fisprocess/service/ShardAwareExecutorService.java` (pending)

### Modified Files
- `src/main/java/com/bracit/fisprocess/service/impl/LedgerPersistenceServiceImpl.java` (pending)
- `src/main/java/com/bracit/fisprocess/messaging/JournalWriteWorker.java` (pending)
- `src/main/java/com/bracit/fisprocess/config/ShardDataSourceConfig.java` (enhanced)
- `build.gradle` (pending)

### Scripts
- `deploy/citus/setup-citus.sh` (existing, enhance)
- `deploy/load-test/k6-load-test.js` (pending)
