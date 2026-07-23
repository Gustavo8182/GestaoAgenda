package br.com.agendaplatform.waitlist.infrastructure;

import br.com.agendaplatform.waitlist.domain.WaitlistEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

    List<WaitlistEntry> findAllByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    Optional<WaitlistEntry> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
