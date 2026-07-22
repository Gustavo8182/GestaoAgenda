package br.com.agendaplatform.availability.infrastructure;

import br.com.agendaplatform.availability.domain.BusinessHours;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessHoursRepository extends JpaRepository<BusinessHours, UUID> {

    List<BusinessHours> findAllByOrganizationId(UUID organizationId);

    @Modifying
    @Query("delete from BusinessHours b where b.organizationId = :organizationId")
    void deleteAllByOrganizationId(@Param("organizationId") UUID organizationId);
}
