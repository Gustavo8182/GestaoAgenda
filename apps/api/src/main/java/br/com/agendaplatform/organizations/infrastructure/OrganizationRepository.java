package br.com.agendaplatform.organizations.infrastructure;

import br.com.agendaplatform.organizations.domain.Organization;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}
