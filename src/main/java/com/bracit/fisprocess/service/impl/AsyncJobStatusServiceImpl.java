package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.enums.AsyncJobStatus;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.messaging.JournalWriteReply;
import com.bracit.fisprocess.service.AsyncJobStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncJobStatusServiceImpl implements AsyncJobStatusService {

    private static final Duration JOB_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "fis:async:job:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Value("${fis.async.cleanup-cron:0 0 4 * * *}")
    private String cleanupCron;

    @Override
    public void createPendingJob(UUID trackingId, UUID tenantId) {
        AsyncJobStatusInfo statusInfo = AsyncJobStatusInfo.builder()
                .status(AsyncJobStatus.PENDING)
                .tenantId(tenantId)
                .submittedAt(OffsetDateTime.now())
                .build();
        saveStatusInfo(trackingId, statusInfo);
        log.debug("Created PENDING job for trackingId={}", trackingId);
    }

    @Override
    public void markProcessing(UUID trackingId) {
        AsyncJobStatusInfo statusInfo = getStatusInfo(trackingId);
        if (statusInfo == null) {
            log.warn("Cannot mark job {} as PROCESSING - not found", trackingId);
            return;
        }
        statusInfo.setStatus(AsyncJobStatus.PROCESSING);
        saveStatusInfo(trackingId, statusInfo);
        log.debug("Marked job {} as PROCESSING", trackingId);
    }

    @Override
    public void markCompleted(UUID trackingId, JournalEntryResponseDto journalEntry) {
        AsyncJobStatusInfo statusInfo = getStatusInfo(trackingId);
        if (statusInfo == null) {
            log.warn("Cannot mark job {} as COMPLETED - not found", trackingId);
            return;
        }
        statusInfo.setStatus(AsyncJobStatus.COMPLETED);
        statusInfo.setProcessedAt(OffsetDateTime.now());
        statusInfo.setJournalEntry(journalEntry);
        saveStatusInfo(trackingId, statusInfo);
        log.info("Marked job {} as COMPLETED - journalEntryId={}", trackingId, journalEntry != null ? journalEntry.getJournalEntryId() : null);
    }

    @Override
    public void markFailed(UUID trackingId, String errorMessage, @Nullable String errorCode) {
        AsyncJobStatusInfo statusInfo = getStatusInfo(trackingId);
        if (statusInfo == null) {
            log.warn("Cannot mark job {} as FAILED - not found", trackingId);
            return;
        }
        statusInfo.setStatus(AsyncJobStatus.FAILED);
        statusInfo.setProcessedAt(OffsetDateTime.now());
        statusInfo.setErrorMessage(errorMessage);
        statusInfo.setErrorCode(errorCode);
        saveStatusInfo(trackingId, statusInfo);
        log.error("Marked job {} as FAILED - error={}", trackingId, errorMessage);
    }

    @Override
    @Nullable
    public JournalWriteReply getJobStatus(UUID trackingId) {
        AsyncJobStatusInfo statusInfo = getStatusInfo(trackingId);
        if (statusInfo == null) {
            return null;
        }
        return toReply(statusInfo);
    }

    @Override
    @Nullable
    public AsyncJobStatus getStatus(UUID trackingId) {
        AsyncJobStatusInfo statusInfo = getStatusInfo(trackingId);
        return statusInfo != null ? statusInfo.getStatus() : null;
    }

    @Override
    public OffsetDateTime getSubmittedAt(UUID trackingId) {
        AsyncJobStatusInfo statusInfo = getStatusInfo(trackingId);
        return statusInfo != null ? statusInfo.getSubmittedAt() : null;
    }

    @Override
    public void cleanupStaleJobs() {
        // This can be implemented as a scheduled task if needed
        log.info("Async job cleanup triggered");
    }

    private void saveStatusInfo(UUID trackingId, AsyncJobStatusInfo statusInfo) {
        String key = KEY_PREFIX + trackingId;
        try {
            String json = jsonMapper.writeValueAsString(statusInfo);
            redisTemplate.opsForValue().set(key, json, JOB_TTL);
        } catch (Exception e) {
            log.error("Failed to save job status for trackingId={}", trackingId, e);
        }
    }

    @Nullable
    private AsyncJobStatusInfo getStatusInfo(UUID trackingId) {
        String key = KEY_PREFIX + trackingId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return jsonMapper.readValue(json, AsyncJobStatusInfo.class);
        } catch (Exception e) {
            log.error("Failed to get job status for trackingId={}", trackingId, e);
            return null;
        }
    }

    private JournalWriteReply toReply(AsyncJobStatusInfo statusInfo) {
        return JournalWriteReply.builder()
                .trackingId(statusInfo.getTrackingId())
                .success(statusInfo.getStatus() == AsyncJobStatus.COMPLETED)
                .journalEntry(statusInfo.getJournalEntry())
                .errorMessage(statusInfo.getErrorMessage())
                .errorCode(statusInfo.getErrorCode())
                .build();
    }
}