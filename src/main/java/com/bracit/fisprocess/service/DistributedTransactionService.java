package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface DistributedTransactionService {

    TransactionResult executeJournalPostingWithSaga(
            UUID tenantId,
            DistributedTransactionUnit unit,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent);

    void compensate(TransactionId transactionId);

    TransactionStatus getTransactionStatus(TransactionId transactionId);

    record TransactionId(UUID id, UUID tenantId) {}

    record TransactionResult(
            boolean success,
            @Nullable JournalEntryResponseDto journalEntry,
            @Nullable String errorMessage,
            @Nullable TransactionId transactionId) {}

    enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMMITTED,
        COMPENSATED,
        FAILED
    }

    interface DistributedTransactionUnit {
        void execute() throws Exception;
        void compensate();
        String getUnitName();
    }
}