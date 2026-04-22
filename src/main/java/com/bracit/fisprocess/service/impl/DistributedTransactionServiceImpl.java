package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.service.DistributedTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DistributedTransactionServiceImpl implements DistributedTransactionService {

    private static final Duration TRANSACTION_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final boolean crossShardEnabled;

    private final ConcurrentHashMap<UUID, TransactionRecord> activeTransactions = new ConcurrentHashMap<>();

    public DistributedTransactionServiceImpl(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            @Value("${fis.sharding.cross-shard-transactions:false}") boolean crossShardEnabled) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.crossShardEnabled = crossShardEnabled;
    }

    @Override
    public TransactionResult executeJournalPostingWithSaga(
            UUID tenantId,
            DistributedTransactionUnit unit,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent) {

        TransactionId transactionId = new TransactionId(UUID.randomUUID(), tenantId);

        if (!crossShardEnabled) {
            log.warn("Cross-shard transactions disabled, executing without saga pattern");
            try {
                unit.execute();
                return new TransactionResult(true, null, null, transactionId);
            } catch (Exception e) {
                log.error("Transaction {} failed: {}", transactionId.id(), e.getMessage());
                return new TransactionResult(false, null, e.getMessage(), transactionId);
            }
        }

        log.info("Starting saga transaction {} for unit {}", transactionId.id(), unit.getUnitName());

        TransactionRecord record = new TransactionRecord(transactionId);
        record.units.add(unit);
        activeTransactions.put(transactionId.id(), record);

        List<DistributedTransactionUnit> executedUnits = new ArrayList<>();

        try {
            record.status = TransactionStatus.PROCESSING;
            saveTransactionRecord(record);

            unit.execute();
            executedUnits.add(unit);
            record.status = TransactionStatus.COMMITTED;
            saveTransactionRecord(record);

            log.info("Saga transaction {} completed successfully", transactionId.id());
            return new TransactionResult(true, null, null, transactionId);

        } catch (Exception e) {
            log.error("Saga transaction {} failed: {}", transactionId.id(), e.getMessage());

            for (DistributedTransactionUnit executedUnit : executedUnits) {
                try {
                    executedUnit.compensate();
                    log.info("Compensated unit: {}", executedUnit.getUnitName());
                } catch (Exception compensationEx) {
                    log.error("Compensation failed for unit {}: {}",
                            executedUnit.getUnitName(), compensationEx.getMessage());
                }
            }

            record.status = TransactionStatus.COMPENSATED;
            saveTransactionRecord(record);

            return new TransactionResult(false, null, e.getMessage(), transactionId);
        } finally {
            activeTransactions.remove(transactionId.id());
        }
    }

    @Override
    public void compensate(TransactionId transactionId) {
        log.info("Compensating transaction {}", transactionId.id());

        String key = "fis:saga:transaction:" + transactionId.id();
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            log.warn("No transaction record found for {}", transactionId.id());
            return;
        }

        try {
            TransactionRecord record = jsonMapper.readValue(json, TransactionRecord.class);

            for (DistributedTransactionUnit unit : record.units) {
                try {
                    unit.compensate();
                } catch (Exception e) {
                    log.error("Compensation failed for unit {}: {}", unit.getUnitName(), e.getMessage());
                }
            }

            record.status = TransactionStatus.COMPENSATED;
            saveTransactionRecord(record);

        } catch (Exception e) {
            log.error("Failed to load transaction record for compensation: {}", e.getMessage());
        }
    }

    @Override
    public TransactionStatus getTransactionStatus(TransactionId transactionId) {
        String key = "fis:saga:transaction:" + transactionId.id();
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return TransactionStatus.FAILED;
        }

        try {
            TransactionRecord record = jsonMapper.readValue(json, TransactionRecord.class);
            return record.status;
        } catch (Exception e) {
            return TransactionStatus.FAILED;
        }
    }

    private void saveTransactionRecord(TransactionRecord record) {
        String key = "fis:saga:transaction:" + record.transactionId.id();
        try {
            String json = jsonMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(key, json, TRANSACTION_TTL);
        } catch (Exception e) {
            log.error("Failed to save transaction record: {}", e.getMessage());
        }
    }

    private static class TransactionRecord {
        public UUID id;
        public TransactionId transactionId;
        public TransactionStatus status = TransactionStatus.PENDING;
        public List<DistributedTransactionUnit> units = new ArrayList<>();
        public OffsetDateTime createdAt;
        public OffsetDateTime updatedAt;

        TransactionRecord() {}

        TransactionRecord(TransactionId transactionId) {
            this.id = transactionId.id();
            this.transactionId = transactionId;
            this.createdAt = OffsetDateTime.now();
            this.updatedAt = OffsetDateTime.now();
        }
    }
}