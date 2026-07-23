package br.com.agendaplatform.clients;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Permite que outros módulos confirmem que uma cliente pertence à organização informada e
 * obtenham seu nome para exibição, sem acessar as classes internas do módulo clients.
 */
public interface ClientLookup {

    Optional<ClientRef> find(UUID clientId, UUID organizationId);

    List<ClientExportRow> listAll(UUID organizationId);
}
