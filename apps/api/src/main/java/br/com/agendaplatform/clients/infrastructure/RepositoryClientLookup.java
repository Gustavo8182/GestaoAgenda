package br.com.agendaplatform.clients.infrastructure;

import br.com.agendaplatform.clients.ClientExportRow;
import br.com.agendaplatform.clients.ClientLookup;
import br.com.agendaplatform.clients.ClientRef;
import java.util.List;
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

    @Override
    public List<ClientExportRow> listAll(UUID organizationId) {
        return clientRepository.findAllByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(client -> new ClientExportRow(
                        client.getName(),
                        client.getPhone(),
                        client.getAlternatePhone(),
                        client.getOrigin(),
                        client.getNotes()))
                .toList();
    }
}
