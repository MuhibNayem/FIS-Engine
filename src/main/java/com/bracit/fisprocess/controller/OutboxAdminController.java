package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.dto.response.OutboxStatsResponseDto;
import com.bracit.fisprocess.repository.OutboxEventRepository;
import com.bracit.fisprocess.scheduling.OutboxAlertJob;
import com.bracit.fisprocess.service.DeadLetterQueueService;
import com.bracit.fisprocess.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Admin-only endpoints for monitoring and intervening on the outbox
 * and dead-letter queue.
 * <p>
 * All routes are under {@code /v1/admin/outbox/**} which is secured
 * to the {@code FIS_ADMIN} role in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/admin/outbox")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('FIS_ADMIN')")
@ApiVersion(1)
public class OutboxAdminController {

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterQueueService deadLetterQueueService;
    private final OutboxAlertJob outboxAlertJob;
    private final OutboxService outboxService;

    /**
     * GET /v1/admin/outbox/stats
     * Returns aggregated outbox statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<OutboxStatsResponseDto> stats() {
        OutboxStatsResponseDto stats = OutboxStatsResponseDto.builder()
                .unpublishedCount(outboxEventRepository.countByPublishedFalseAndDlqFalse())
                .dlqSize(deadLetterQueueService.dlqSize())
                .oldestUnpublishedAgeSeconds(outboxAlertJob.getOldestAgeSeconds())
                .retryStreak(outboxAlertJob.getRetryStreak())
                .build();
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /v1/admin/outbox/pending
     * Lists unpublished (non-DLQ) events, paginated.
     */
    @GetMapping("/pending")
    public ResponseEntity<Page<OutboxEvent>> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        Page<OutboxEvent> events = outboxEventRepository.findPendingEvents(pageRequest);
        return ResponseEntity.ok(events);
    }

    /**
     * POST /v1/admin/outbox/{id}/retry
     * Forces a retry of a specific unpublished event by resetting its
     * retry state so the relay job can pick it up again.
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryEvent(@PathVariable("id") UUID id) {
        var event = outboxEventRepository.findActiveEventById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Unpublished event not found: " + id));

        event.setRetryCount(0);
        event.setLastError(null);
        outboxEventRepository.save(event);
        log.info("Admin forced retry on outbox event outboxId='{}'", id);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outboxId", id);
        body.put("message", "Event reset and will be retried by relay job");
        return ResponseEntity.ok(body);
    }

    /**
     * POST /v1/admin/outbox/{id}/discard
     * Marks a specific unpublished event as dead-lettered (discarded).
     */
    @PostMapping("/{id}/discard")
    public ResponseEntity<Map<String, Object>> discardEvent(@PathVariable("id") UUID id) {
        var event = outboxEventRepository.findActiveEventById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Unpublished event not found: " + id));

        deadLetterQueueService.moveToDlq(id, "Manually discarded by admin");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outboxId", id);
        body.put("message", "Event moved to DLQ");
        return ResponseEntity.ok(body);
    }

    /**
     * GET /v1/admin/outbox/dlq
     * Lists events in the dead-letter queue, paginated.
     */
    @GetMapping("/dlq")
    public ResponseEntity<Page<OutboxEvent>> dlq(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OutboxEvent> events = deadLetterQueueService.listDlq(pageRequest);
        return ResponseEntity.ok(events);
    }

    /**
     * POST /v1/admin/outbox/dlq/{id}/retry
     * Resets a DLQ event so it can be re-published.
     */
    @PostMapping("/dlq/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryFromDlq(@PathVariable("id") UUID id) {
        boolean ok = deadLetterQueueService.retryFromDlq(id);
        if (!ok) {
            throw new ResponseStatusException(NOT_FOUND, "DLQ event not found: " + id);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outboxId", id);
        body.put("message", "DLQ event reset and will be retried by relay job");
        return ResponseEntity.ok(body);
    }

    /**
     * POST /v1/admin/outbox/dlq/{id}/discard
     * Permanently deletes a DLQ event.
     */
    @PostMapping("/dlq/{id}/discard")
    public ResponseEntity<Map<String, Object>> discardFromDlq(@PathVariable("id") UUID id) {
        boolean ok = deadLetterQueueService.discardFromDlq(id);
        if (!ok) {
            throw new ResponseStatusException(NOT_FOUND, "DLQ event not found: " + id);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outboxId", id);
        body.put("message", "DLQ event permanently discarded");
        return ResponseEntity.ok(body);
    }
}
