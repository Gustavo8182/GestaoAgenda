package br.com.agendaplatform.clients.infrastructure;

import br.com.agendaplatform.clients.domain.Client;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<Client> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select count(c) > 0 from Client c where c.organizationId = :organizationId and "
            + "(c.phoneNormalized in :normalizedPhones or c.alternatePhoneNormalized in :normalizedPhones)")
    boolean existsAnyWithNormalizedPhoneIn(
            @Param("organizationId") UUID organizationId, @Param("normalizedPhones") Collection<String> normalizedPhones);

    @Query("select c from Client c where c.organizationId = :organizationId and ("
            + "lower(c.name) like lower(concat('%', :query, '%')) "
            + "or (:normalizedQuery <> '' and c.phoneNormalized like concat('%', :normalizedQuery, '%')) "
            + "or (:normalizedQuery <> '' and c.alternatePhoneNormalized like concat('%', :normalizedQuery, '%'))) "
            + "order by c.name asc")
    List<Client> search(
            @Param("organizationId") UUID organizationId,
            @Param("query") String query,
            @Param("normalizedQuery") String normalizedQuery);
}
