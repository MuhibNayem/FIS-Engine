package com.bracit.fisprocess.service;
import com.bracit.fisprocess.dto.request.MatchLineRequestDto;
import com.bracit.fisprocess.dto.request.StartReconciliationRequestDto;
import com.bracit.fisprocess.dto.response.OutstandingItemsReportDto;
import com.bracit.fisprocess.dto.response.ReconciliationResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public interface ReconciliationService {
    ReconciliationResponseDto start(UUID tenantId, StartReconciliationRequestDto req, String performedBy);
    List<MatchResult> autoMatch(UUID tenantId, UUID reconciliationId);
    MatchResult manualMatch(UUID tenantId, UUID reconciliationId, MatchLineRequestDto req);
    MatchResult unmatch(UUID tenantId, UUID matchId);
    ReconciliationResponseDto complete(UUID tenantId, UUID reconciliationId, String performedBy);
    ReconciliationResponseDto getById(UUID tenantId, UUID id);
    Page<ReconciliationResponseDto> list(UUID tenantId, @Nullable UUID bankAccountId, @Nullable String status, Pageable pageable);
    OutstandingItemsReportDto getOutstandingItems(UUID tenantId, UUID bankAccountId, LocalDate asOfDate);
    record MatchResult(UUID id, UUID statementLineId, UUID journalLineId, Long amount, String matchType) {}
}
