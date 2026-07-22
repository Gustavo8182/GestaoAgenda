package br.com.agendaplatform.clients.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.clients.domain.Client;
import br.com.agendaplatform.clients.domain.PhoneNormalizer;
import br.com.agendaplatform.clients.infrastructure.ClientRepository;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    public CreateClientResult create(String name, String phone, String alternatePhone, String origin, String notes) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        Client client = new Client(organizationId, name, phone, alternatePhone, origin, notes);

        Set<String> normalizedPhones = new LinkedHashSet<>();
        normalizedPhones.add(client.getPhoneNormalized());
        if (client.getAlternatePhoneNormalized() != null) {
            normalizedPhones.add(client.getAlternatePhoneNormalized());
        }
        boolean possibleDuplicate =
                clientRepository.existsAnyWithNormalizedPhoneIn(organizationId, normalizedPhones);

        clientRepository.save(client);

        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), "CLIENT_CREATED", "CLIENT", client.getId());

        return new CreateClientResult(ClientSummary.from(client), possibleDuplicate);
    }

    @Transactional(readOnly = true)
    public List<ClientSummary> list(String query) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        List<Client> clients = (query == null || query.isBlank())
                ? clientRepository.findAllByOrganizationIdOrderByNameAsc(organizationId)
                : clientRepository.search(organizationId, query.trim(), PhoneNormalizer.normalize(query));

        return clients.stream().map(ClientSummary::from).toList();
    }
}
