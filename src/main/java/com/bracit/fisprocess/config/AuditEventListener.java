package com.bracit.fisprocess.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class AuditEventListener {

    @EventListener
    public void onApplicationEvent(org.springframework.context.ApplicationEvent event) {
        if (event instanceof org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent ||
            event instanceof org.springframework.context.event.ContextClosedEvent ||
            event.getClass().getName().contains("Authorization")) {

            Map<String, Object> auditEntry = new HashMap<>();
            auditEntry.put("event", event.getClass().getSimpleName());
            auditEntry.put("timestamp", OffsetDateTime.now());
            auditEntry.put("source", event.getSource() != null ? event.getSource().getClass().getSimpleName() : "N/A");

            log.warn("AUDIT: {}", auditEntry);
        }
    }
}