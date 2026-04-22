package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.domain.enums.AsyncJobStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryBatchRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.response.AsyncJobResponseDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.messaging.JournalWriteMessage;
import com.bracit.fisprocess.messaging.JournalWriteReply;
import com.bracit.fisprocess.service.AsyncJobStatusService;
import com.bracit.fisprocess.service.AsyncJournalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncJournalServiceImpl implements AsyncJournalService {

    private final RabbitTemplate rabbitTemplate;
    private final AsyncJobStatusService asyncJobStatusService;
    private final JsonMapper jsonMapper;

    @Value("${fis.async.reply-timeout-seconds:30}")
    private int replyTimeoutSeconds;

    @Value("${fis.async.worker-concurrency:10}")
    private int workerConcurrency;

    @Override
    public AsyncJobResponseDto submitAsyncJournalEntry(
            UUID tenantId,
            CreateJournalEntryRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent) {

        UUID trackingId = UUID.randomUUID();

        asyncJobStatusService.createPendingJob(trackingId, tenantId);

        JournalWriteMessage message = JournalWriteMessage.builder()
                .trackingId(trackingId)
                .tenantId(tenantId)
                .actorRoleHeader(actorRoleHeader)
                .traceparent(traceparent)
                .request(request)
                .build();

        sendToQueue(message);

        log.info("Submitted async journal entry with trackingId={}, tenantId={}", trackingId, tenantId);

        return waitForReply(trackingId);
    }

    @Override
    public AsyncJobResponseDto submitAsyncJournalEntryBatch(
            UUID tenantId,
            CreateJournalEntryBatchRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent) {

        List<AsyncJobResponseDto> results = new ArrayList<>();

        for (CreateJournalEntryRequestDto entry : request.getEntries()) {
            UUID trackingId = UUID.randomUUID();
            asyncJobStatusService.createPendingJob(trackingId, tenantId);

            JournalWriteMessage message = JournalWriteMessage.builder()
                    .trackingId(trackingId)
                    .tenantId(tenantId)
                    .actorRoleHeader(actorRoleHeader)
                    .traceparent(traceparent)
                    .request(entry)
                    .build();

            sendToQueue(message);
            results.add(waitForReply(trackingId));
        }

        return results.getFirst();
    }

    @Override
    public AsyncJobResponseDto getJobStatus(UUID trackingId) {
        JournalWriteReply reply = asyncJobStatusService.getJobStatus(trackingId);
        if (reply == null) {
            return null;
        }
        return toAsyncJobResponse(reply);
    }

    private void sendToQueue(JournalWriteMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMqTopology.JOURNAL_WRITE_EXCHANGE,
                RabbitMqTopology.JOURNAL_WRITE_ROUTING_KEY,
                message,
                msg -> {
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    msg.getMessageProperties().setReplyTo(RabbitMqTopology.JOURNAL_WRITE_REPLY_QUEUE);
                    msg.getMessageProperties().setCorrelationId(message.getTrackingId().toString());
                    return msg;
                });
    }

    private AsyncJobResponseDto waitForReply(UUID trackingId) {
        String replyQueue = RabbitMqTopology.JOURNAL_WRITE_REPLY_QUEUE;

        long startTime = System.currentTimeMillis();
        long timeoutMs = replyTimeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            JournalWriteReply reply = asyncJobStatusService.getJobStatus(trackingId);

            if (reply != null) {
                return toAsyncJobResponse(reply);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.warn("Timeout waiting for reply for trackingId={}", trackingId);
        return AsyncJobResponseDto.builder()
                .trackingId(trackingId)
                .status(AsyncJobStatus.PROCESSING.name())
                .build();
    }

    private AsyncJobResponseDto toAsyncJobResponse(JournalWriteReply reply) {
        AsyncJobStatus status;
        if (reply.isSuccess()) {
            status = AsyncJobStatus.COMPLETED;
        } else {
            status = AsyncJobStatus.FAILED;
        }

        return AsyncJobResponseDto.builder()
                .trackingId(reply.getTrackingId())
                .status(status.name())
                .journalEntry(reply.getJournalEntry())
                .errorMessage(reply.getErrorMessage())
                .errorCode(reply.getErrorCode())
                .build();
    }
}