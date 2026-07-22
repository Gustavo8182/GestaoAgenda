package br.com.agendaplatform.catalog.infrastructure;

import br.com.agendaplatform.catalog.domain.Service;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    List<Service> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);
}
