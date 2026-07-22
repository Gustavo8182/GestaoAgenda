package br.com.agendaplatform.clients.infrastructure;

import br.com.agendaplatform.clients.ClientLookup;
import br.com.agendaplatform.clients.ClientRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RepositoryClientLookup implements ClientLookup {

    private final ClientRepository clientRepository;

    RepositoryClientLookup(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public Optional<ClientRef> find(UUID clientId, UUID organizationId) {
        return clientRepository
                .findByIdAndOrganizationId(clientId, organizationId)
                .map(client -> new ClientRef(client.getId(), client.getName()));
    }
}
