package br.com.agendaplatform.availability;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Permite que outros módulos consultem bloqueios de uma organização para fins de indicadores
 * (dashboard, relatórios), sem acessar as classes internas do módulo availability.
 */
public interface BlockLookup {

    List<BlockSummary> findOverlapping(UUID organizationId, Instant startInclusive, Instant endExclusive);
}
