package br.com.agendaplatform.clients.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.clients.domain.Client;
import br.com.agendaplatform.clients.infrastructure.ClientRepository;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientRegistry {

    private final ClientRepository clientRepository;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;

    ClientRegistry(
            ClientRepository clientRepository,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder) {
        this.clientRepository = clientRepository;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public CreateClientResult create(String name, String phone) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        Client client = new Client(organizationId, name, phone);

        boolean possibleDuplicate = !clientRepository
                .findAllByOrganizationIdAndPhoneNormalized(organizationId, client.getPhoneNormalized())
                .isEmpty();

        clientRepository.save(client);

        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), "CLIENT_CREATED", "CLIENT", client.getId());

        return new CreateClientResult(ClientSummary.from(client), possibleDuplicate);
    }

    @Transactional(readOnly = true)
    public List<ClientSummary> list() {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return clientRepository.findAllByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(ClientSummary::from)
                .toList();
    }
}
