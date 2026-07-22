package br.com.agendaplatform.auditing.infrastructure;

import br.com.agendaplatform.auditing.AuditRecorder;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JpaAuditRecorder implements AuditRecorder {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    JpaAuditRecorder(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Override
    public void record(UUID organizationId, UUID actorUserId, String action, String entityType, UUID entityId) {
        record(organizationId, actorUserId, action, entityType, entityId, Map.of());
    }

    @Override
    public void record(
            UUID organizationId,
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, String> metadata) {
        auditLogRepository.save(
                new AuditLog(organizationId, actorUserId, action, entityType, entityId, metadata, clock.instant()));
    }
}
