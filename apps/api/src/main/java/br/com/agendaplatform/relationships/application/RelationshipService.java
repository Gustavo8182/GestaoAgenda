package br.com.agendaplatform.relationships.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.clients.ClientRef;
import br.com.agendaplatform.clients.ClientRegistration;
import br.com.agendaplatform.identity.UserLookup;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.relationships.RelationshipExportRow;
import br.com.agendaplatform.relationships.RelationshipOverview;
import br.com.agendaplatform.relationships.domain.RelationshipContact;
import br.com.agendaplatform.relationships.domain.RelationshipContactNotFoundException;
import br.com.agendaplatform.relationships.domain.RelationshipStatus;
import br.com.agendaplatform.relationships.infrastructure.RelationshipContactRepository;
import br.com.agendaplatform.scheduling.AppointmentBooking;
import br.com.agendaplatform.scheduling.AppointmentSummary;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelationshipService implements RelationshipOverview {

    private final RelationshipContactRepository relationshipContactRepository;
    private final ClientRegistration clientRegistration;
    private final AppointmentBooking appointmentBooking;
    private final UserLookup userLookup;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    RelationshipService(
            RelationshipContactRepository relationshipContactRepository,
            ClientRegistration clientRegistration,
            AppointmentBooking appointmentBooking,
            UserLookup userLookup,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.relationshipContactRepository = relationshipContactRepository;
        this.clientRegistration = clientRegistration;
        this.appointmentBooking = appointmentBooking;
        this.userLookup = userLookup;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public RelationshipSummary create(String name, String phone, String origin) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        UUID responsibleUserId = currentActorProvider.currentUserId();

        RelationshipContact contact =
                new RelationshipContact(organizationId, name, phone, origin, responsibleUserId, clock.instant());
        relationshipContactRepository.save(contact);

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "RELATIONSHIP_CONTACT_CREATED",
                "RELATIONSHIP_CONTACT",
                contact.getId());

        return toSummary(contact);
    }

    @Transactional(readOnly = true)
    public List<RelationshipSummary> list() {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return relationshipContactRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public RelationshipSummary update(UUID contactId, RelationshipStatus status, String nextAction, Instant nextActionAt) {
        RelationshipContact contact = findOrThrow(contactId);
        Instant now = clock.instant();

        if (status != null) {
            contact.updateStatus(status, now);
        }
        if (nextAction != null || nextActionAt != null) {
            contact.updateNextAction(nextAction, nextActionAt, now);
        }
        relationshipContactRepository.save(contact);

        auditRecorder.record(
                contact.getOrganizationId(),
                currentActorProvider.currentUserId(),
                "RELATIONSHIP_CONTACT_UPDATED",
                "RELATIONSHIP_CONTACT",
                contact.getId());

        return toSummary(contact);
    }

    @Transactional
    public AppointmentSummary convert(UUID contactId, UUID serviceId, Instant startAt, Instant endAt) {
        RelationshipContact contact = findOrThrow(contactId);

        UUID clientId = contact.getClientId();
        if (clientId == null) {
            ClientRef client =
                    clientRegistration.register(contact.getName(), contact.getPhone(), null, contact.getOrigin(), null);
            clientId = client.id();
        }

        AppointmentSummary appointment = appointmentBooking.create(clientId, serviceId, startAt, endAt);

        contact.convert(clientId, appointment.id(), clock.instant());
        relationshipContactRepository.save(contact);

        auditRecorder.record(
                contact.getOrganizationId(),
                currentActorProvider.currentUserId(),
                "RELATIONSHIP_CONTACT_CONVERTED",
                "RELATIONSHIP_CONTACT",
                contact.getId(),
                Map.of("clientId", clientId.toString(), "appointmentId", appointment.id().toString()));

        return appointment;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipExportRow> findAll(UUID organizationId) {
        return relationshipContactRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .map(contact -> new RelationshipExportRow(
                        contact.getName(),
                        contact.getPhone(),
                        contact.getOrigin(),
                        contact.getStatus().name(),
                        contact.getLastInteractionAt(),
                        contact.getNextAction(),
                        contact.getNextActionAt(),
                        userLookup.find(contact.getResponsibleUserId()).map(user -> user.displayName()).orElse("Usuária removida")))
                .toList();
    }

    private RelationshipContact findOrThrow(UUID contactId) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return relationshipContactRepository
                .findByIdAndOrganizationId(contactId, organizationId)
                .orElseThrow(() -> new RelationshipContactNotFoundException("Contato não encontrado."));
    }

    private RelationshipSummary toSummary(RelationshipContact contact) {
        String responsibleName = userLookup
                .find(contact.getResponsibleUserId())
                .map(user -> user.displayName())
                .orElse("Usuária removida");

        return new RelationshipSummary(
                contact.getId(),
                contact.getName(),
                contact.getPhone(),
                contact.getOrigin(),
                contact.getStatus().name(),
                contact.getLastInteractionAt(),
                contact.getNextAction(),
                contact.getNextActionAt(),
                responsibleName,
                contact.getClientId(),
                contact.getAppointmentId(),
                contact.isPendingContact(clock.instant()));
    }
}
