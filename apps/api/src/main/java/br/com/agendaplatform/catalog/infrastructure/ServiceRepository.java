package br.com.agendaplatform.catalog.infrastructure;

import br.com.agendaplatform.catalog.domain.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    List<Service> findAllByOrganizationIdOrderByDisplayOrderAscNameAsc(UUID organizationId);

    Optional<Service> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select coalesce(max(s.displayOrder), -1) + 1 from Service s where s.organizationId = :organizationId")
    int nextDisplayOrder(@Param("organizationId") UUID organizationId);
}
