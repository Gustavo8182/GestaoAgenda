package br.com.agendaplatform.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * Permite que outros módulos confirmem que um serviço pertence à organização informada e
 * obtenham seu nome/duração, sem acessar as classes internas do módulo catalog.
 */
public interface ServiceLookup {

    Optional<ServiceRef> find(UUID serviceId, UUID organizationId);
}
