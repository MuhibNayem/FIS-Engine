# FIS-Engine Capacity Planning Guide

## Document Control

| Field | Value |
|-------|-------|
| **Version** | 1.0 |
| **Date** | April 13, 2026 |
| **Owner** | FIS Platform SRE Team |
| **Review Cycle** | Monthly |

---

## 1. Overview

This guide provides resource sizing, scaling guidelines, and capacity planning for the FIS-Engine platform. All numbers are based on benchmark results and production observations.

### Baseline Assumptions

| Parameter | Value |
|-----------|-------|
| Journal entry size | ~4KB (with 4 lines) |
| Average journal lines per entry | 4 (2 debit, 2 credit) |
| Hash chain computation | SHA-256 per entry |
| Idempotency check | Redis SETNX (hot path) |
| Double-entry validation | In-memory, O(n) on lines |

---

## 2. Application Tier (FIS-Process)

### 2.1 Resource Requirements per 1,000 TPS

| Resource | Per Pod | Total (3 pods) | Notes |
|----------|---------|----------------|-------|
| **CPU** | 500m - 1 core | 1.5 - 3 cores | Depends on journal complexity |
| **Memory** | 512Mi - 1Gi | 1.5 - 3Gi | JVM heap + metaspace |
| **Network** | 100 Mbps | 300 Mbps | JSON payload + TLS overhead |

### 2.2 JVM Sizing

```
-Xms512m -Xmx1536m           # 50%-75% of container memory (2Gi limit)
-XX:MaxRAMPercentage=75.0     # Dynamic heap based on container
-XX:MetaspaceSize=128m        # Class metadata
-XX:MaxMetaspaceSize=256m     # Cap metaspace
```

### 2.3 Connection Pool Sizing

| Pool | Per Pod | Total (3 pods) | Formula |
|------|---------|----------------|---------|
| **HikariCP** | 30 connections | 90 connections | `(cores × 2) + effective_spindles` |
| **Redis (Lettuce)** | 20 connections | 60 connections | 1 per thread + burst buffer |
| **RabbitMQ** | 10 connections | 30 connections | 1 for publisher + 1 for consumer per queue |

### 2.4 Horizontal Scaling Guidelines

| Metric | Scale Up | Scale Down |
|--------|----------|------------|
| **CPU utilization** | > 70% average for 5 min | < 40% average for 10 min |
| **Memory utilization** | > 80% average for 5 min | < 50% average for 10 min |
| **HTTP queue depth** | > 50 queued requests | < 10 queued requests |
| **Request latency (p95)** | > 100ms for 5 min | < 50ms for 10 min |

### 2.5 Scaling Limits

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| **Min replicas** | 3 | Multi-AZ deployment, PDB requires 2 available |
| **Max replicas** | 20 | Database connection pool limit (200 max connections) |
| **Scale-up rate** | Max 4 pods per 60s | Prevents thundering herd |
| **Scale-down rate** | Max 2 pods per 60s | Prevents over-aggressive downscaling |
| **Stabilization window (up)** | 60s | Allows metrics to settle |
| **Stabilization window (down)** | 300s | Prevents flapping |

---

## 3. Database Tier (PostgreSQL)

### 3.1 Storage Sizing

| Component | Per 1M Entries | Growth Rate | Notes |
|-----------|---------------|-------------|-------|
| **Table data** | ~4GB | 4GB/month at 1K TPS | ~4KB per entry with 4 lines |
| **Indexes** | ~2GB | 2GB/month | B-tree on tenant_id, sequence, hash |
| **WAL segments** | ~500MB | Rolling | 16MB per segment, recycled |
| **Total per 1M entries** | ~6.5GB | — | Excluding TOAST and bloat |

### 3.2 Volume Sizing by Throughput

| Daily Volume | Annual Growth | Recommended Volume | Retention |
|--------------|---------------|--------------------|-----------|
| 10M entries | 3.6B entries | 250GB | 1 year + 20% buffer |
| 50M entries | 18B entries | 1TB | 1 year + 20% buffer |
| 100M entries | 36B entries | 2.5TB | 1 year + 20% buffer |

### 3.3 Connection Sizing

| Metric | Value | Notes |
|--------|-------|-------|
| **max_connections** | 200 | PostgreSQL config |
| **Reserved (superuser)** | 5 | For maintenance |
| **Available for app** | 195 | HikariCP pools + direct connections |
| **Per-pod pool** | 30 | HikariCP maximum |
| **Max pods supported** | 6 | `195 / 30 = 6.5` → 6 pods |
| **With PgBouncer** | 50+ | Connection pooling reduces per-pod to 1-2 |

> **Recommendation**: Add PgBouncer when scaling beyond 6 application pods.

### 3.4 Compute Sizing

| Throughput | vCPUs | Memory | Storage IOPS | Notes |
|------------|-------|--------|-------------|-------|
| < 500 TPS | 2 | 4Gi | 3,000 | Baseline |
| 500 - 2,000 TPS | 4 | 8Gi | 5,000 | Normal production |
| 2,000 - 5,000 TPS | 8 | 16Gi | 10,000 | High throughput |
| > 5,000 TPS | 16 | 32Gi | 16,000 | Read replicas recommended |

### 3.5 When to Add Read Replicas

| Trigger | Action |
|---------|--------|
| Read:Write ratio > 5:1 | Add 1 read replica |
| Reporting queries slow down | Add 1 read replica for analytics |
| Primary CPU > 80% sustained | Add read replica + offload reads |
| Cross-region read latency matters | Add replica in target region |

---

## 4. Cache Tier (Redis)

### 4.1 Memory Requirements

| Use Case | Per Entry | Entries (72h TTL) | Total Memory |
|----------|-----------|--------------------|-------------|
| **Idempotency keys** | ~200 bytes | ~260M (at 1K TPS) | ~50GB |
| **Session data** | ~1KB | ~100K active sessions | ~100MB |
| **Rate limiting counters** | ~100 bytes | ~10K tenants × 100 keys | ~100MB |
| **Total (headroom 2x)** | — | — | **~100GB** |

### 4.2 Memory Policy

```
maxmemory 2gb                    # Per Redis instance
maxmemory-policy allkeys-lru     # Evict least recently used
maxmemory-samples 10             # Better LRU approximation
```

### 4.3 Replica Sizing

| Throughput | Replicas | Memory per Replica | Notes |
|------------|----------|--------------------|-------|
| < 2,000 TPS | 2 | 2Gi | Standard HA |
| 2,000 - 5,000 TPS | 3 | 4Gi | Extra replica for read distribution |
| > 5,000 TPS | 4+ | 4Gi+ | Consider Redis Cluster (sharding) |

### 4.4 When to Scale Redis

| Trigger | Action |
|---------|--------|
| Memory usage > 80% | Increase `maxmemory` or add replica |
| Eviction rate > 100/sec | Increase memory or reduce TTL |
| Latency > 5ms (p99) | Check network, consider Redis Cluster |
| CPU > 70% | Redis is single-threaded; split data or use Cluster |

---

## 5. Message Broker Tier (RabbitMQ)

### 5.1 Queue Depth Guidelines

| Queue | Max Depth | Alert Threshold | Consumer Count |
|-------|-----------|-----------------|----------------|
| **outbox.events** | 1,000,000 | > 100,000 | 3 per pod |
| **journal.posted** | 500,000 | > 50,000 | 2 per pod |
| **dead-letter** | 100,000 | > 1,000 | Manual inspection |

### 5.2 Message Size

| Message Type | Average Size | Notes |
|--------------|-------------|-------|
| Outbox event | ~2KB | JSON payload |
| Journal posted | ~4KB | Full entry with lines |
| Account created | ~500B | Account metadata |

### 5.3 When to Scale RabbitMQ

| Trigger | Action |
|---------|--------|
| Queue depth growing continuously | Add more consumers |
| Node memory > 70% | Add node to cluster |
| Message rate > 10K/sec | Enable lazy queues |
| Connection count > 1,000 | Review connection pooling |

### 5.4 Quorum Queue Configuration

```
x-queue-type: quorum           # Use quorum queues for durability
x-quorum-initial-group-size: 3 # Replicate to 3 nodes
x-delivery-limit: 10000        # Max redeliveries before DLQ
x-dead-letter-exchange: dlx    # Dead letter exchange
```

---

## 6. Network & Bandwidth

### 6.1 Bandwidth Requirements

| Direction | Per 1,000 TPS | Notes |
|-----------|---------------|-------|
| **Ingress (API)** | ~100 Mbps | JSON payloads ~1KB avg |
| **Egress (API responses)** | ~50 Mbps | Responses ~500B avg |
| **Internal (app → DB)** | ~200 Mbps | Queries + result sets |
| **Internal (app → Redis)** | ~10 Mbps | Idempotency checks |
| **Internal (app → RabbitMQ)** | ~50 Mbps | Message publishing |

### 6.2 Ingress Controller

| Parameter | Value | Notes |
|-----------|-------|-------|
| **Max connections** | 10,000 | NGINX `worker_connections` |
| **Request rate limit** | 100 RPS per IP | Prevent abuse |
| **Request body size** | 10MB | Journal entry batch uploads |
| **Timeout** | 30s | Application processing time |

---

## 7. Multi-Region Capacity

### 7.1 Active-Passive (DR)

| Component | Primary | DR | Notes |
|-----------|---------|-----|-------|
| **Application** | Full capacity | 50% capacity | Scale up on failover |
| **PostgreSQL** | Primary + replicas | Warm standby (streaming replication) | RPO < 5 min |
| **Redis** | Primary + replicas | Cold (rebuild from DB) | RPO = last backup |
| **RabbitMQ** | 3-node cluster | Cold (recreate topology) | Messages in outbox will replay |

### 7.2 Active-Active (Future)

| Component | Region A | Region B | Notes |
|-----------|----------|----------|-------|
| **Application** | Full capacity | Full capacity | Tenant-aware routing |
| **PostgreSQL** | Primary (writes) | Read replica | Multi-region reads |
| **Redis** | Local cluster | Local cluster | Idempotency is local |
| **RabbitMQ** | Federation | Federation | Cross-region message sync |

---

## 8. Cost Estimation

### 8.1 Per 1,000 TPS (Monthly, AWS us-east-1)

| Component | Size | Count | Monthly Cost |
|-----------|------|-------|-------------|
| **EKS Nodes (m6i.xlarge)** | 4 vCPU, 16Gi | 3 | ~$350 |
| **PostgreSQL (db.r6i.xlarge)** | 4 vCPU, 32Gi | 1 primary + 1 replica | ~$600 |
| **PostgreSQL Storage (gp3)** | 250GB | 1 | ~$25 |
| **Redis (cache.r6g.large)** | 2 vCPU, 13Gi | 1 primary + 2 replicas | ~$400 |
| **RabbitMQ (m6i.large)** | 2 vCPU, 8Gi | 3 nodes | ~$300 |
| **Load Balancer (NLB)** | — | 1 | ~$25 |
| **S3 (backups)** | 10GB/month | — | ~$1 |
| **Data transfer** | ~500GB/month | — | ~$50 |
| **Total** | — | — | **~$1,750/month** |

### 8.2 Cost per 1M Journal Entries

```
$1,750 / (1,000 TPS × 86,400 sec/day × 30 days) × 1,000,000
= $1,750 / 2,592,000,000 × 1,000,000
= $0.000675 per entry (≈ $0.675 per 1,000 entries)
```

---

## 9. Scaling Runbook

### 9.1 Scale Up (Horizontal)

```bash
# 1. Check current HPA status
kubectl get hpa -n fis-production

# 2. Manually scale (if HPA is too slow)
kubectl scale deployment fis-process --replicas=6 -n fis-production

# 3. Monitor new pods
kubectl get pods -n fis-production -w

# 4. Verify connection pool distribution
kubectl exec -it fis-process-<pod> -n fis-production -- \
  curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

### 9.2 Scale Up (Vertical - Database)

```bash
# 1. Update PostgreSQL resource requests in postgres-statefulset.yaml
# requests: cpu: "4" -> "8"
# requests: memory: "8Gi" -> "16Gi"

# 2. Apply changes (StatefulSet rolling update)
kubectl apply -f deploy/k8s/production/postgres-statefulset.yaml

# 3. Monitor during rolling update
kubectl rollout status statefulset/postgres -n fis-production --timeout=600s
```

### 9.3 Scale Up (Redis)

```bash
# 1. Add more replicas
kubectl scale statefulset redis-replica --replicas=3 -n fis-production

# 2. Verify replication
kubectl exec -it redis-primary-0 -n fis-production -- \
  redis-cli -a $REDIS_PASSWORD info replication
```

### 9.4 When to Add More Replicas

| Component | Add replica when... |
|-----------|---------------------|
| **FIS-Process** | CPU > 70% for 10+ minutes, or latency SLA breached |
| **PostgreSQL** | Read latency > 50ms (add read replica), or CPU > 80% |
| **Redis** | Memory > 80%, or eviction rate > 100/sec |
| **RabbitMQ** | Queue depth growing, or message rate > 10K/sec |

---

## 10. Performance Tuning Checklist

### 10.1 Application

- [ ] G1GC enabled with max pause time target
- [ ] HikariCP pool sized to `(cores × 2) + spindles`
- [ ] Redis connection pooling (Lettuce pool) configured
- [ ] RabbitMQ publisher confirms enabled
- [ ] Compression enabled for API responses
- [ ] Graceful shutdown configured (60s drain)

### 10.2 Database

- [ ] `shared_buffers = 25%` of RAM
- [ ] `effective_cache_size = 75%` of RAM
- [ ] `work_mem` sized for concurrent sort/hash operations
- [ ] Autovacuum tuned for write-heavy tables
- [ ] Indexes on foreign keys and query predicates
- [ ] `random_page_cost = 1.1` for SSD

### 10.3 Redis

- [ ] `maxmemory` set with appropriate eviction policy
- [ ] `appendonly yes` for persistence
- [ ] `appendfsync everysec` for performance/durability balance
- [ ] Dangerous commands renamed/disabled

### 10.4 RabbitMQ

- [ ] Quorum queues for critical messages
- [ ] Lazy queues for high-depth queues
- [ ] Publisher confirms enabled
- [ ] Consumer prefetch configured (`x-qos`)
- [ ] Dead letter exchange configured

---

## Appendix: Benchmark Results

### JMH Benchmark Targets

| Benchmark | Mode | Target (ops/ms) | Notes |
|-----------|------|-----------------|-------|
| `JournalPostingEngineBenchmark.benchmarkSimpleJournalPosting` | thrpt | > 5.0 | 2-line entry |
| `JournalPostingEngineBenchmark.benchmarkComplexJournalPosting` | thrpt | > 2.0 | 10-line entry |
| `HashChainBenchmark.benchmarkSmallEntryHash` | thrpt | > 50.0 | SHA-256 with 2 lines |
| `HashChainBenchmark.benchmarkLargeEntryHash` | thrpt | > 10.0 | SHA-256 with 100 lines |
| `IdempotencyBenchmark.benchmarkFirstTimeCheck` | thrpt | > 100.0 | SETNX path |
| `IdempotencyBenchmark.benchmarkDuplicateCheck` | thrpt | > 200.0 | Cache hit path |
| `ValidationBenchmark.benchmarkSimpleValidation` | thrpt | > 500.0 | 2-line validation |
| `ValidationBenchmark.benchmarkWideValidation` | thrpt | > 50.0 | 50-line validation |

Run benchmarks:
```bash
./gradlew jmh
```
