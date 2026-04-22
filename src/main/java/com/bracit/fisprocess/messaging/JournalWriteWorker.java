package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.AsyncJobStatusService;
import com.bracit.fisprocess.service.Shard;
import com.bracit.fisprocess.service.ShardContextHolder;
import com.bracit.fisprocess.service.ShardRouter;
import com.bracit.fisprocess.service.impl.JournalPostingEngine;
import com.rabbitmq.client.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@Slf4j
public class JournalWriteWorker {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final JournalPostingEngine journalPostingEngine;
    private final AsyncJobStatusService asyncJobStatusService;
    private final BusinessEntityRepository businessEntityRepository;
    private final ModelMapper modelMapper;
    private final JsonMapper jsonMapper;
    private final RabbitTemplate rabbitTemplate;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    private final ShardRouter shardRouter;

    public JournalWriteWorker(
            JournalPostingEngine journalPostingEngine,
            AsyncJobStatusService asyncJobStatusService,
            BusinessEntityRepository businessEntityRepository,
            ModelMapper modelMapper,
            JsonMapper jsonMapper,
            RabbitTemplate rabbitTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            ShardRouter shardRouter) {
        this.journalPostingEngine = journalPostingEngine;
        this.asyncJobStatusService = asyncJobStatusService;
        this.businessEntityRepository = businessEntityRepository;
        this.modelMapper = modelMapper;
        this.jsonMapper = jsonMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("journalWriteWorker");
        this.meterRegistry = meterRegistry;
        this.shardRouter = shardRouter;
    }

    @RabbitListener(queues = RabbitMqTopology.JOURNAL_WRITE_QUEUE)
    public void consume(JournalWriteMessage message, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received journal write message: trackingId={}, tenantId={}",
                message.getTrackingId(), message.getTenantId());

        if (message == null || message.getTrackingId() == null || message.getTenantId() == null) {
            log.error("Rejecting malformed journal write message: {}", message);
            channel.basicReject(deliveryTag, false);
            return;
        }

        Shard shard = determineShard(message);
        meterRegistry.counter("fis.worker.messages.received", "shard", shard.name()).increment();

        try {
            asyncJobStatusService.markProcessing(message.getTrackingId());

            JournalEntryResponseDto result = executeWithCircuitBreakerAndRetry(message);

            asyncJobStatusService.markCompleted(message.getTrackingId(), result);
            sendReply(message.getTrackingId(), true, result, null, null);
            channel.basicAck(deliveryTag, false);
            log.info("Successfully processed journal write: trackingId={}, journalEntryId={}",
                    message.getTrackingId(), result.getJournalEntryId());

            meterRegistry.counter("fis.worker.messages.success", "shard", shard.name()).increment();

        } catch (Exception ex) {
            log.error("Failed to process journal write: trackingId={}, error={}",
                    message.getTrackingId(), ex.getMessage(), ex);

            asyncJobStatusService.markFailed(message.getTrackingId(), ex.getMessage(), ex.getClass().getSimpleName());
            sendReply(message.getTrackingId(), false, null, ex.getMessage(), ex.getClass().getSimpleName());

            meterRegistry.counter("fis.worker.messages.error", "shard", shard.name()).increment();

            if (isRetryableError(ex)) {
                log.warn("Retryable error for trackingId={}, requeuing", message.getTrackingId());
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("Non-retryable error for trackingId={}, sending to DLQ", message.getTrackingId());
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    private Shard determineShard(JournalWriteMessage message) {
        try {
            return shardRouter.getShardForTenant(message.getTenantId());
        } catch (Exception e) {
            log.warn("Could not determine shard for tenant {}, using SHARD_1", message.getTenantId());
            return Shard.SHARD_1;
        }
    }

    private JournalEntryResponseDto executeWithCircuitBreakerAndRetry(JournalWriteMessage message) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Supplier<JournalEntryResponseDto> decoratedSupplier = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    () -> executeJournalPost(message)
            );

            Exception lastException = null;
            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    return decoratedSupplier.get();
                } catch (Exception ex) {
                    lastException = ex;
                    log.warn("Attempt {}/{} failed for trackingId={}: {}",
                            attempt, MAX_RETRY_ATTEMPTS, message.getTrackingId(), ex.getMessage());

                    if (attempt < MAX_RETRY_ATTEMPTS && isRetryableError(ex)) {
                        long backoffMs = (long) Math.pow(2, attempt - 1) * 100;
                        Thread.sleep(backoffMs);
                    }
                }
            }
            throw lastException;
        } finally {
            sample.stop(Timer.builder("fis.worker.process.duration")
                    .tag("shard", determineShard(message).name())
                    .register(meterRegistry));
        }
    }

    private JournalEntryResponseDto executeJournalPost(JournalWriteMessage message) {
        BusinessEntity tenant = businessEntityRepository.findById(message.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + message.getTenantId()));

        DraftJournalEntry draft = buildDraft(message.getTenantId(), tenant, message.getRequest());

        shardRouter.getShardForTenant(message.getTenantId());

        return journalPostingEngine.post(
                message.getTenantId(),
                draft,
                message.getActorRoleHeader(),
                message.getTraceparent());
    }

    private DraftJournalEntry buildDraft(UUID tenantId, BusinessEntity tenant, CreateJournalEntryRequestDto request) {
        LocalDate effectiveDate = request.getEffectiveDate() != null
                ? request.getEffectiveDate()
                : request.getPostedDate();
        LocalDate transactionDate = request.getTransactionDate() != null
                ? request.getTransactionDate()
                : request.getPostedDate();

        DraftJournalEntry draft = modelMapper.map(request, DraftJournalEntry.class);
        draft.setTenantId(tenantId);
        draft.setBaseCurrency(tenant.getBaseCurrency());
        draft.setEffectiveDate(effectiveDate);
        draft.setTransactionDate(transactionDate);
        return draft;
    }

    private void sendReply(UUID trackingId, boolean success, @Nullable JournalEntryResponseDto journalEntry,
            @Nullable String errorMessage, @Nullable String errorCode) {
        try {
            JournalWriteReply reply = JournalWriteReply.builder()
                    .trackingId(trackingId)
                    .success(success)
                    .journalEntry(journalEntry)
                    .errorMessage(errorMessage)
                    .errorCode(errorCode)
                    .build();

            rabbitTemplate.convertAndSend(RabbitMqTopology.JOURNAL_WRITE_REPLY_QUEUE, reply);
        } catch (Exception e) {
            log.error("Failed to send reply for trackingId={}", trackingId, e);
        }
    }

    private boolean isRetryableError(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("Connection refused") ||
                message.contains("timeout") ||
                message.contains("deadlock") ||
                message.contains("lock timeout") ||
                ex instanceof java.net.SocketException ||
                ex instanceof java.sql.SQLTimeoutException ||
                ex instanceof org.springframework.dao.CannotAcquireLockException;
    }
}