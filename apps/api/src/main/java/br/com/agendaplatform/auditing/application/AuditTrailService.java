package br.com.agendaplatform.auditing.application;

import br.com.agendaplatform.auditing.infrastructure.AuditLog;
import br.com.agendaplatform.auditing.infrastructure.AuditLogRepository;
import br.com.agendaplatform.identity.UserLookup;
import br.com.agendaplatform.identity.UserRef;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditTrailService {

    private static final int MAX_ENTRIES = 200;

    private final AuditLogRepository auditLogRepository;
    private final UserLookup userLookup;
    private final CurrentOrganizationProvider currentOrganizationProvider;

    AuditTrailService(
            AuditLogRepository auditLogRepository,
            UserLookup userLookup,
            CurrentOrganizationProvider currentOrganizationProvider) {
        this.auditLogRepository = auditLogRepository;
        this.userLookup = userLookup;
        this.currentOrganizationProvider = currentOrganizationProvider;
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> recent() {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return auditLogRepository
                .findByOrganizationIdOrderByOccurredAtDesc(organizationId, PageRequest.of(0, MAX_ENTRIES))
                .stream()
                .map(this::toEntry)
                .toList();
    }

    private AuditEntry toEntry(AuditLog log) {
        String actorName = log.getActorUserId() == null
                ? "Sistema"
                : userLookup.find(log.getActorUserId()).map(UserRef::displayName).orElse("Usuária removida");

        return new AuditEntry(
                log.getId(), actorName, log.getAction(), log.getEntityType(), log.getEntityId(), log.getMetadata(),
                log.getOccurredAt());
    }
}
