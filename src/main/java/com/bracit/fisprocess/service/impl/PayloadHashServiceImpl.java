package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.service.PayloadHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Deterministic SHA-256 hash implementation based on canonical JSON.
 */
@Service
@RequiredArgsConstructor
public class PayloadHashServiceImpl implements PayloadHashService {

    private final JsonMapper jsonMapper;

    @Override
    public String sha256Hex(Object payload) {
        try {
            Object canonicalPayload = canonicalize(payload);
            byte[] bytes = jsonMapper.writeValueAsString(canonicalPayload).getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return toHex(hash);
        } catch (RuntimeException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute payload hash", e);
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalize).toList();
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value == null) {
            return value;
        }
        if (value instanceof UUID || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            return String.valueOf(value);
        }
        Object normalized = jsonMapper.convertValue(value, LinkedHashMap.class);
        return canonicalize(normalized);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
