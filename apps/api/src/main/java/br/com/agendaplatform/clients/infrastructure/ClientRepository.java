package br.com.agendaplatform.clients.infrastructure;

import br.com.agendaplatform.clients.domain.Client;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    List<Client> findAllByOrganizationIdAndPhoneNormalized(UUID organizationId, String phoneNormalized);
}
