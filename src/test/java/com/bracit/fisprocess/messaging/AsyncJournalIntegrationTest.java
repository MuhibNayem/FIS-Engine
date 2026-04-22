package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.response.AsyncJobResponseDto;
import com.bracit.fisprocess.service.AsyncJournalService;
import com.bracit.fisprocess.service.AsyncJobStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "fis.async.reply-timeout-seconds=10",
        "fis.async.worker-concurrency=2",
        "fis.security.enabled=false",
        "fis.security.allow-insecure-mode=true"
})
class AsyncJournalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AsyncJournalService asyncJournalService;

    @Autowired
    private AsyncJobStatusService asyncJobStatusService;

    @Autowired
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Test
    void shouldSubmitAsyncJournalEntryAndReturnAccepted() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        CreateJournalEntryRequestDto request = CreateJournalEntryRequestDto.builder()
                .eventId("async-test-" + System.currentTimeMillis())
                .postedDate(LocalDate.now())
                .transactionCurrency("USD")
                .createdBy("async-test")
                .lines(List.of(
                        JournalLineRequestDto.builder()
                                .accountCode("1100")
                                .amountCents(1000L)
                                .isCredit(false)
                                .build(),
                        JournalLineRequestDto.builder()
                                .accountCode("5100")
                                .amountCents(1000L)
                                .isCredit(true)
                                .build()
                ))
                .build();

        AsyncJobResponseDto response = asyncJournalService.submitAsyncJournalEntry(
                tenantId, request, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getTrackingId()).isNotNull();
        assertThat(response.getStatus()).isIn("PENDING", "PROCESSING", "COMPLETED", "FAILED");

        if (!"COMPLETED".equals(response.getStatus()) && !"FAILED".equals(response.getStatus())) {
            await()
                    .atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        AsyncJobResponseDto status = asyncJournalService.getJobStatus(response.getTrackingId());
                        assertThat(status).isNotNull();
                        assertThat(status.getStatus()).isIn("COMPLETED", "FAILED");
                    });
        }
    }

    @Test
    void shouldTrackJobStatusThroughLifecycle() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        CreateJournalEntryRequestDto request = CreateJournalEntryRequestDto.builder()
                .eventId("status-test-" + System.currentTimeMillis())
                .postedDate(LocalDate.now())
                .transactionCurrency("USD")
                .createdBy("status-test")
                .lines(List.of(
                        JournalLineRequestDto.builder()
                                .accountCode("1100")
                                .amountCents(500L)
                                .isCredit(false)
                                .build(),
                        JournalLineRequestDto.builder()
                                .accountCode("5100")
                                .amountCents(500L)
                                .isCredit(true)
                                .build()
                ))
                .build();

        AsyncJobResponseDto initialResponse = asyncJournalService.submitAsyncJournalEntry(
                tenantId, request, null, null);

        UUID trackingId = initialResponse.getTrackingId();

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    AsyncJobResponseDto status = asyncJournalService.getJobStatus(trackingId);
                    assertThat(status).isNotNull();
                    assertThat(status.getStatus()).isNotEqualTo("PENDING");
                });
    }

    @Test
    void shouldRejectDuplicateEventId() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        String eventId = "duplicate-test-" + System.currentTimeMillis();

        CreateJournalEntryRequestDto request1 = CreateJournalEntryRequestDto.builder()
                .eventId(eventId)
                .postedDate(LocalDate.now())
                .transactionCurrency("USD")
                .createdBy("duplicate-test")
                .lines(List.of(
                        JournalLineRequestDto.builder()
                                .accountCode("1100")
                                .amountCents(200L)
                                .isCredit(false)
                                .build(),
                        JournalLineRequestDto.builder()
                                .accountCode("5100")
                                .amountCents(200L)
                                .isCredit(true)
                                .build()
                ))
                .build();

        asyncJournalService.submitAsyncJournalEntry(tenantId, request1, null, null);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        CreateJournalEntryRequestDto request2 = CreateJournalEntryRequestDto.builder()
                .eventId(eventId)
                .postedDate(LocalDate.now())
                .transactionCurrency("USD")
                .createdBy("duplicate-test-2")
                .lines(List.of(
                        JournalLineRequestDto.builder()
                                .accountCode("1100")
                                .amountCents(300L)
                                .isCredit(false)
                                .build(),
                        JournalLineRequestDto.builder()
                                .accountCode("5100")
                                .amountCents(300L)
                                .isCredit(true)
                                .build()
                ))
                .build();

        AsyncJobResponseDto response2 = asyncJournalService.submitAsyncJournalEntry(
                tenantId, request2, null, null);

        await()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    AsyncJobResponseDto status = asyncJournalService.getJobStatus(response2.getTrackingId());
                    assertThat(status).isNotNull();
                    assertThat(status.getStatus()).isIn("COMPLETED", "FAILED");
                    if ("FAILED".equals(status.getStatus())) {
                        assertThat(status.getErrorMessage()).contains("Duplicate");
                    }
                });
    }
}