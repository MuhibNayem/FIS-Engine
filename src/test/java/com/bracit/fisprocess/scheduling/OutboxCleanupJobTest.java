package com.bracit.fisprocess.scheduling;

import com.bracit.fisprocess.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxCleanupJob Unit Tests")
class OutboxCleanupJobTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Spy
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private OutboxCleanupJob outboxCleanupJob;

    @Test
    @DisplayName("should delete published entries older than retention period")
    void shouldDeletePublishedEntriesOlderThanRetention() {
        ReflectionTestUtils.setField(outboxCleanupJob, "retentionDays", 30);
        when(outboxEventRepository.deletePublishedBefore(any(OffsetDateTime.class))).thenReturn(5);

        outboxCleanupJob.purgePublishedEntries();

        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(outboxEventRepository).deletePublishedBefore(cutoffCaptor.capture());

        OffsetDateTime capturedCutoff = cutoffCaptor.getValue();
        OffsetDateTime expectedCutoff = OffsetDateTime.now().minusDays(30);
        // Allow 5 seconds tolerance for test execution time
        assertThat(capturedCutoff).isCloseTo(expectedCutoff,
                org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("should use configured retention days")
    void shouldUseConfiguredRetentionDays() {
        ReflectionTestUtils.setField(outboxCleanupJob, "retentionDays", 7);
        when(outboxEventRepository.deletePublishedBefore(any(OffsetDateTime.class))).thenReturn(0);

        outboxCleanupJob.purgePublishedEntries();

        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(outboxEventRepository).deletePublishedBefore(cutoffCaptor.capture());

        OffsetDateTime capturedCutoff = cutoffCaptor.getValue();
        OffsetDateTime expectedCutoff = OffsetDateTime.now().minusDays(7);
        assertThat(capturedCutoff).isCloseTo(expectedCutoff,
                org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("should handle zero deletions gracefully")
    void shouldHandleZeroDeletions() {
        ReflectionTestUtils.setField(outboxCleanupJob, "retentionDays", 30);
        when(outboxEventRepository.deletePublishedBefore(any(OffsetDateTime.class))).thenReturn(0);

        // Should not throw
        outboxCleanupJob.purgePublishedEntries();

        verify(outboxEventRepository).deletePublishedBefore(any(OffsetDateTime.class));
    }
}
