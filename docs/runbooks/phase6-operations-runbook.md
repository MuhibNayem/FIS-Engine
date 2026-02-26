# Phase 6 Operations Runbook

## DLQ inspection and replay
1. Inspect queue: `fis.ingestion.dlq.queue` from RabbitMQ management UI.
2. Export failed payloads and identify business vs transient failures.
3. Fix mapping/config/data issue.
4. Replay to `fis.events.exchange` with corrected routing key.

## Period close + revaluation
1. Ensure FX rates uploaded for period end date.
2. Soft close period via `/v1/accounting-periods/{id}/status`.
3. Trigger revaluation via `/v1/revaluations/periods/{id}`.
4. Hard close period.

## Exchange rate upload
1. Upload via `/v1/exchange-rates` as `FIS_ADMIN`.
2. Verify with `/v1/exchange-rates` query endpoint.

## Emergency reopen
1. Reopen latest period first (cascading rule).
2. Reopen earlier period only after all subsequent periods are open.
3. Re-post corrective entries as append-only corrections.
