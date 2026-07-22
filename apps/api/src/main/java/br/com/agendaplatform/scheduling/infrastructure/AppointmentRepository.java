package br.com.agendaplatform.scheduling.infrastructure;

import br.com.agendaplatform.scheduling.domain.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findAllByOrganizationIdOrderByStartAtAsc(UUID organizationId);

    @Query("select count(a) > 0 from Appointment a where a.organizationId = :organizationId "
            + "and a.startAt < :endAt and a.endAt > :startAt")
    boolean existsOverlapping(
            @Param("organizationId") UUID organizationId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);
}
