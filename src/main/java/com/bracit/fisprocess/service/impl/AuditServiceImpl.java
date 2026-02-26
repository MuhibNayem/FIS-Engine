package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AuditLog;
import com.bracit.fisprocess.domain.enums.AuditAction;
import com.bracit.fisprocess.domain.enums.AuditEntityType;
import com.bracit.fisprocess.repository.AuditLogRepository;
import com.bracit.fisprocess.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void logChange(
            UUID tenantId,
            AuditEntityType entityType,
            UUID entityId,
            AuditAction action,
            @Nullable Map<String, Object> oldValue,
            @Nullable Map<String, Object> newValue,
            String performedBy) {
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .performedBy(performedBy)
                .build());
    }
}
