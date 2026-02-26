package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.enums.AuditAction;
import com.bracit.fisprocess.domain.enums.AuditEntityType;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public interface AuditService {

    void logChange(
            UUID tenantId,
            AuditEntityType entityType,
            UUID entityId,
            AuditAction action,
            @Nullable Map<String, Object> oldValue,
            @Nullable Map<String, Object> newValue,
            String performedBy);
}
