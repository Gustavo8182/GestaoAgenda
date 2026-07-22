package br.com.agendaplatform.auditing.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntry(
        UUID id,
        String actorName,
        String action,
        String entityType,
        UUID entityId,
        Map<String, String> metadata,
        Instant occurredAt) {
}
