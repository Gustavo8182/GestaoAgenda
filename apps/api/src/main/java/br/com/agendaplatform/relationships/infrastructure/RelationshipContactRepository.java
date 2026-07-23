package br.com.agendaplatform.relationships.infrastructure;

import br.com.agendaplatform.relationships.domain.RelationshipContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelationshipContactRepository extends JpaRepository<RelationshipContact, UUID> {

    List<RelationshipContact> findAllByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    Optional<RelationshipContact> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
