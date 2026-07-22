package br.com.agendaplatform.availability;

import java.time.Instant;
import java.util.UUID;

/**
 * Permite que outros módulos validem um intervalo de horário contra o horário de funcionamento
 * e os bloqueios da organização, sem acessar as classes internas do módulo availability.
 */
public interface AvailabilityCheck {

    /**
     * Se a organização ainda não configurou nenhum horário de funcionamento, não há restrição
     * (comportamento anterior à existência deste recurso é preservado até a configuração ser feita).
     */
    boolean isWithinBusinessHours(UUID organizationId, String timezone, Instant startAt, Instant endAt);

    boolean overlapsBlock(UUID organizationId, Instant startAt, Instant endAt);
}
