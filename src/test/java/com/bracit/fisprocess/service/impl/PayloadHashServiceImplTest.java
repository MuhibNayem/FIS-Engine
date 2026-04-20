package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.service.PayloadHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayloadHashServiceImpl Unit Tests")
class PayloadHashServiceImplTest {

    private PayloadHashService service;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new PayloadHashServiceImpl(jsonMapper);
    }

    @Test
    @DisplayName("should produce deterministic hash for same input")
    void shouldProduceDeterministicHash() {
        Map<String, Object> payload = Map.of("eventId", "EVT-001", "amount", 1000, "currency", "USD");

        String hash1 = service.sha256Hex(payload);
        String hash2 = service.sha256Hex(payload);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    @DisplayName("should produce different hash for different input")
    void shouldProduceDifferentHashForDifferentInput() {
        Map<String, Object> payload1 = Map.of("amount", 1000);
        Map<String, Object> payload2 = Map.of("amount", 2000);

        String hash1 = service.sha256Hex(payload1);
        String hash2 = service.sha256Hex(payload2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("should produce same hash regardless of Map key order")
    void shouldIgnoreMapKeyOrder() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("a", 1);
        ordered.put("b", 2);
        ordered.put("c", 3);

        Map<String, Object> reversed = new LinkedHashMap<>();
        reversed.put("c", 3);
        reversed.put("b", 2);
        reversed.put("a", 1);

        assertThat(service.sha256Hex(ordered)).isEqualTo(service.sha256Hex(reversed));
    }

    @Test
    @DisplayName("should hash UUID as string")
    void shouldHashUuid() {
        UUID uuid = UUID.randomUUID();
        String hash = service.sha256Hex(uuid);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should hash Enum as string")
    void shouldHashEnum() {
        String hash = service.sha256Hex(Thread.State.RUNNABLE);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should hash LocalDate as string")
    void shouldHashLocalDate() {
        String hash = service.sha256Hex(LocalDate.of(2026, 4, 13));
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should handle nested maps")
    void shouldHandleNestedMaps() {
        Map<String, Object> payload = Map.of(
                "outer", Map.of("inner", "value"),
                "key", "simple");
        String hash = service.sha256Hex(payload);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should handle lists")
    void shouldHandleLists() {
        Map<String, Object> payload = Map.of("items", List.of("a", "b", "c"));
        String hash = service.sha256Hex(payload);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should handle null value in map")
    void shouldHandleNullInMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", null);
        payload.put("other", "value");
        String hash = service.sha256Hex(payload);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should handle plain objects via Jackson conversion")
    void shouldHandlePlainObjects() {
        // Using a simple POJO-like structure via LinkedHashMap
        Map<String, Object> payload = Map.of("name", "test", "value", 42);
        String hash = service.sha256Hex(payload);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should handle empty map")
    void shouldHandleEmptyMap() {
        String hash = service.sha256Hex(Map.of());
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should handle string input")
    void shouldHandleString() {
        String hash = service.sha256Hex("simple-string");
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should handle number input")
    void shouldHandleNumber() {
        String hash = service.sha256Hex(42);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("should handle boolean input")
    void shouldHandleBoolean() {
        String hash1 = service.sha256Hex(true);
        String hash2 = service.sha256Hex(false);
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }
}
