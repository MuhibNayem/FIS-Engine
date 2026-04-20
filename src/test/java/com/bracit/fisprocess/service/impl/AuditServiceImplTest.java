package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AuditLog;
import com.bracit.fisprocess.domain.enums.AuditAction;
import com.bracit.fisprocess.domain.enums.AuditEntityType;
import com.bracit.fisprocess.repository.AuditLogRepository;
import com.bracit.fisprocess.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditServiceImpl Unit Tests")
class AuditServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    private AuditService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(auditLogRepository);
    }

    @Test
    @DisplayName("should log change with old and new values")
    void shouldLogChangeWithValues() {
        Map<String, Object> oldVal = Map.of("name", "Old Name");
        Map<String, Object> newVal = Map.of("name", "New Name");

        service.logChange(TENANT_ID, AuditEntityType.ACCOUNT, ENTITY_ID,
                AuditAction.UPDATED, oldVal, newVal, "admin-user");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertThat(log.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(log.getEntityType()).isEqualTo(AuditEntityType.ACCOUNT);
        assertThat(log.getEntityId()).isEqualTo(ENTITY_ID);
        assertThat(log.getAction()).isEqualTo(AuditAction.UPDATED);
        assertThat(log.getOldValue()).isEqualTo(oldVal);
        assertThat(log.getNewValue()).isEqualTo(newVal);
        assertThat(log.getPerformedBy()).isEqualTo("admin-user");
    }

    @Test
    @DisplayName("should log change with null old and new values")
    void shouldLogChangeWithNullValues() {
        service.logChange(TENANT_ID, AuditEntityType.ACCOUNTING_PERIOD, ENTITY_ID,
                AuditAction.DEACTIVATED, null, null, "system");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertThat(log.getOldValue()).isNull();
        assertThat(log.getNewValue()).isNull();
    }

    @Test
    @DisplayName("should log CREATED action")
    void shouldLogCreated() {
        service.logChange(TENANT_ID, AuditEntityType.ACCOUNT, ENTITY_ID,
                AuditAction.CREATED, null, Map.of("code", "1000"), "creator");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.CREATED);
    }
}
