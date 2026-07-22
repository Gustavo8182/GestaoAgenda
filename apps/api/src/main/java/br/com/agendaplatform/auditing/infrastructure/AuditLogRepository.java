package br.com.agendaplatform.auditing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByOrganizationIdOrderByOccurredAtDesc(UUID organizationId, Pageable pageable);
}
