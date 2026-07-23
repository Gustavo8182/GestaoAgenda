package br.com.agendaplatform.scheduling.infrastructure;

import br.com.agendaplatform.scheduling.domain.Appointment;
import br.com.agendaplatform.scheduling.domain.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findAllByOrganizationIdOrderByStartAtAsc(UUID organizationId);

    Optional<Appointment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Appointment> findAllByOrganizationIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            UUID organizationId, Instant startInclusive, Instant startExclusive);

    Optional<Appointment> findFirstByOrganizationIdAndStatusNotInAndStartAtGreaterThanEqualOrderByStartAtAsc(
            UUID organizationId, List<AppointmentStatus> excludedStatuses, Instant startInclusive);

    List<Appointment> findAllByOrganizationIdAndClientIdOrderByStartAtDesc(UUID organizationId, UUID clientId);

    /**
     * {@code effectiveEndAt} já deve vir com o intervalo do próprio novo agendamento somado
     * (ver {@code AppointmentScheduler}) — aqui só falta considerar o intervalo de cada
     * agendamento EXISTENTE, que exige aritmética de intervalo sobre uma coluna (não é
     * portável em JPQL puro), por isso a consulta nativa.
     */
    @Query(
            value = "select count(*) > 0 from appointments a where a.organization_id = :organizationId "
                    + "and a.status not in ('CANCELLED', 'NO_SHOW') "
                    + "and a.id <> :excludeId "
                    + "and a.start_at < :effectiveEndAt "
                    + "and (a.end_at + (a.buffer_minutes * interval '1 minute')) > :startAt",
            nativeQuery = true)
    boolean existsOverlappingExcluding(
            @Param("organizationId") UUID organizationId,
            @Param("startAt") Instant startAt,
            @Param("effectiveEndAt") Instant effectiveEndAt,
            @Param("excludeId") UUID excludeId);
}
