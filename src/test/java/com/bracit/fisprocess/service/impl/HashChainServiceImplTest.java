package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HashChainService Tests")
class HashChainServiceImplTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @InjectMocks
    private HashChainServiceImpl hashChainService;

    @Test
    @DisplayName("should return genesis hash '0' when no entries exist")
    void shouldReturnGenesisHash() {
        UUID tenantId = UUID.randomUUID();
        when(journalEntryRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(Optional.empty());

        String hash = hashChainService.getLatestHash(tenantId);

        assertThat(hash).isEqualTo("0");
    }

    @Test
    @DisplayName("should return last entry hash when entries exist")
    void shouldReturnLastEntryHash() {
        UUID tenantId = UUID.randomUUID();
        JournalEntry last = JournalEntry.builder()
                .hash("abc123")
                .build();

        when(journalEntryRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(Optional.of(last));

        String hash = hashChainService.getLatestHash(tenantId);

        assertThat(hash).isEqualTo("abc123");
    }

    @Test
    @DisplayName("should compute deterministic SHA-256 hash")
    void shouldComputeDeterministicHash() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String previousHash = "0";
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        String hash1 = hashChainService.computeHash(id, previousHash, createdAt);
        String hash2 = hashChainService.computeHash(id, previousHash, createdAt);

        assertThat(hash1).isNotNull().isNotEmpty();
        assertThat(hash1).isEqualTo(hash2); // deterministic
        assertThat(hash1).hasSize(64); // SHA-256 hex length
    }

    @Test
    @DisplayName("should produce different hash for different inputs")
    void shouldProduceDifferentHashForDifferentInputs() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        String hash1 = hashChainService.computeHash(id, "0", createdAt);
        String hash2 = hashChainService.computeHash(id, "different-previous", createdAt);

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
