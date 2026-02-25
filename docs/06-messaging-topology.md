# Low-Level Design: Messaging Topology (RabbitMQ) & Redis

This document defines the async event ingestion infrastructure using RabbitMQ Quorum Queues and the Redis idempotency layer to guarantee zero financial data loss.

---

## 1. Exchanges and Routing

We utilize a Topic Exchange to allow flexible routing of different event types from various upstream domains into the FIS.

- **Exchange Name:** `fis.events.exchange`
- **Exchange Type:** `topic`
- **Durability:** Durable
- **Routing Key Convention:** `{domain}.{entity}.{action}`
  - Examples: `ecommerce.order.completed`, `payroll.salary.disbursed`, `billing.invoice.paid`

---

## 2. Core Intake Queues (Quorum)

FIS listens to a primary intake queue that binds to the exchange using a wildcard routing key.

- **Queue Name:** `fis.ingestion.queue`
- **Queue Type:** `quorum` (Essential for Raft consensus and high availability)
- **Binding:** Bound to `fis.events.exchange` with routing key `*.*.*` (captures all events)
- **Dead Letter Exchange (DLX):** `fis.dlx.exchange`
- **Dead Letter Routing Key:** `fis.ingestion.dlq`
- **Delivery Mode:** `2` (Persistent)
- **Message TTL:** None (messages wait indefinitely until consumed or dead-lettered)

---

## 3. Dead Letter Topology

If a message fails validation (e.g., malformed JSON) or exceeds retry limits, it is routed here.

- **DLX Exchange Name:** `fis.dlx.exchange` (Type: `direct`)
- **DLQ Queue Name:** `fis.ingestion.dlq.queue`
- **Queue Type:** `quorum`
- **Binding:** Bound to `fis.dlx.exchange` with routing key `fis.ingestion.dlq`
- **Purpose:** Holds poison messages for manual inspection. An admin dashboard or scheduled job can inspect and replay them.

---

## 4. Outbound Event Publishing

After a Journal Entry is successfully committed, the FIS publishes domain events for downstream consumers.

- **Exchange Name:** `fis.domain.exchange`
- **Exchange Type:** `topic`
- **Routing Key Convention:** `fis.journal.{action}`
  - Examples: `fis.journal.posted`, `fis.journal.reverted`
- **Pattern:** Transactional Outbox — events are written to an `fis_outbox` table within the same ACID transaction as the journal entry, then relayed to RabbitMQ by a poller or CDC connector.

---

## 5. Consumer Configuration (Spring Boot 4.0.1)

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:fis_service}
    password: ${RABBITMQ_PASS:secret}
    virtual-host: /fis
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 250
        concurrency: 10
        max-concurrency: 50
        default-requeue-rejected: false
    template:
      retry:
        enabled: true
        initial-interval: 1000ms
        max-attempts: 3
        multiplier: 2.0
```

**Key Settings Explained:**
| Setting | Value | Rationale |
|---------|-------|-----------|
| `acknowledge-mode` | `manual` | Never auto-ack. Ack only after Idempotency check + Postgres commit. |
| `prefetch` | `250` | Allows each consumer to buffer 250 messages for Virtual Thread processing. |
| `concurrency` | `10` | Starting consumer count. Each spawns Virtual Threads internally. |
| `max-concurrency` | `50` | Upper bound to prevent overwhelming Redis/Postgres. |
| `default-requeue-rejected` | `false` | Rejected messages go to DLQ, not back to main queue. |

---

## 6. Redis Idempotency Layer — Full Specification

### 6.1 Key Pattern
```
fis:ik:{tenant_id}:{event_id}
```
Examples:
- `fis:ik:2f3d...-tenant:evt_987654321`
- `fis:ik:2f3d...-tenant:pay_run_2026_03`

### 6.2 Value Schema
```json
{
  "status": "PROCESSING | COMPLETED | FAILED",
  "payloadHash": "sha256:abcdef123456...",
  "responseBody": "{ ... cached response JSON ... }",
  "createdAt": "2026-02-25T10:00:00Z"
}
```

### 6.3 Lifecycle
| Phase | Redis Command | Behavior |
|-------|---------------|----------|
| Initial check | `SET fis:ik:{tenantId}:evt_123 {status:PROCESSING,...} NX EX 259200` | Atomic set-if-not-exists with 72-hour TTL (259200 seconds). Returns OK if new. |
| On success | `SET fis:ik:{tenantId}:evt_123 {status:COMPLETED, responseBody:{...}} XX EX 259200` | Updates value to COMPLETED with the cached response. Resets TTL. |
| On failure | `SET fis:ik:{tenantId}:evt_123 {status:FAILED,...} XX EX 259200` | Updates to FAILED. Allows retry with same `eventId` if status is FAILED. |
| On duplicate | `GET fis:ik:{tenantId}:evt_123` | Returns cached response. Compare `payloadHash` — if mismatch, return 409 Conflict. |

### 6.4 TTL & Eviction
- **TTL:** 72 hours (3 days). Covers worst-case weekend retry windows.
- **Eviction Policy:** `volatile-ttl` — Redis evicts keys with the shortest remaining TTL first among keys that have an expiration set.
- **MaxMemory:** Set to a safe value based on expected throughput. At 10K events/sec, 72h of keys ≈ ~2.5 billion keys. In practice, shard Redis or reduce TTL for high-throughput deployments.

### 6.5 Redis Configuration Snippet
```conf
maxmemory 2gb
maxmemory-policy volatile-ttl
```

---

## 7. End-to-End Idempotency Flow

```
1. Upstream System publishes `ecommerce.order.completed` to `fis.events.exchange`.
2. RabbitMQ routes to `fis.ingestion.queue`.
3. @RabbitListener inside FIS consumes the message on a Virtual Thread.
4. Extract `eventId` from payload/request → this is the canonical `ik`.
5. Redis: SET fis:ik:{tenantId}:{eventId} {status:PROCESSING} NX EX 259200
6. IF OK (new):
   → RuleMappingService (ModelMapper: Event → DraftJournalEntry)
   → PeriodValidationService
   → JournalEntryValidationService
   → MultiCurrencyService
   → LedgerLockingService (SELECT FOR UPDATE)
   → LedgerPersistenceService (ACID commit)
   → HashChainService
   → Redis: SET fis:ik:... {status:COMPLETED, responseBody:{...}} XX EX 259200
   → channel.basicAck()
7. IF nil (duplicate):
   → GET fis:ik:...
   → Compare payloadHash
   → IF match: log duplicate, channel.basicAck()
   → IF mismatch: log conflict warning, channel.basicReject(requeue=false) → DLQ
8. IF Exception (DB down):
   → channel.basicNack(requeue=true)
   → Redis key stays PROCESSING, TTL will eventually expire, allowing retry
9. IF Validation Fails (missing amount, unbalanced):
   → Redis: SET status=FAILED
   → channel.basicReject(requeue=false) → DLQ
```
