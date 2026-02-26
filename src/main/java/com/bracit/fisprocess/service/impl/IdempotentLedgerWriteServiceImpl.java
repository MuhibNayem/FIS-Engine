package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.exception.DuplicateIdempotencyKeyException;
import com.bracit.fisprocess.service.IdempotencyService;
import com.bracit.fisprocess.service.IdempotentLedgerWriteService;
import com.bracit.fisprocess.service.PayloadHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotentLedgerWriteServiceImpl implements IdempotentLedgerWriteService {

    private final IdempotencyService idempotencyService;
    private final PayloadHashService payloadHashService;
    private final JsonMapper jsonMapper;

    @Override
    public <T> T execute(
            UUID tenantId,
            String eventId,
            Object payload,
            Class<T> responseType,
            Supplier<T> writeOperation) {
        String payloadHash = payloadHashService.sha256Hex(payload);
        IdempotencyService.IdempotencyCheckResult check =
                idempotencyService.checkAndMarkProcessing(tenantId, eventId, payloadHash);

        if (check.state() == IdempotencyService.IdempotencyState.DUPLICATE_DIFFERENT_PAYLOAD) {
            throw new DuplicateIdempotencyKeyException(eventId);
        }

        if (check.state() == IdempotencyService.IdempotencyState.DUPLICATE_SAME_PAYLOAD) {
            if (check.cachedResponse() != null && !check.cachedResponse().isBlank() && !"{}".equals(check.cachedResponse())) {
                return jsonMapper.readValue(check.cachedResponse(), responseType);
            }
            throw new DuplicateIdempotencyKeyException(eventId);
        }

        try {
            T response = writeOperation.get();
            idempotencyService.markCompleted(tenantId, eventId, payloadHash, jsonMapper.writeValueAsString(response));
            return response;
        } catch (RuntimeException ex) {
            idempotencyService.markFailed(
                    tenantId,
                    eventId,
                    payloadHash,
                    jsonMapper.writeValueAsString(Map.of(
                            "error", ex.getClass().getSimpleName(),
                            "message", ex.getMessage() == null ? "Operation failed" : ex.getMessage())));
            throw ex;
        }
    }
}
