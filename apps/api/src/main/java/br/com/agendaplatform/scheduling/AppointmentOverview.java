package br.com.agendaplatform.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Permite que outros módulos consultem agendamentos de uma organização para fins de indicadores
 * (dashboard, relatórios), sem acessar as classes internas do módulo scheduling.
 */
public interface AppointmentOverview {

    List<AppointmentSummary> findByStartAtBetween(UUID organizationId, Instant startInclusive, Instant endExclusive);

    Optional<AppointmentSummary> findNextUpcoming(UUID organizationId, Instant from);
}
