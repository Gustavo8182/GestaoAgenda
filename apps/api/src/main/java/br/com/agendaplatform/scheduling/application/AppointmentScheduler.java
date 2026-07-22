package br.com.agendaplatform.scheduling.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.catalog.ServiceLookup;
import br.com.agendaplatform.catalog.ServiceRef;
import br.com.agendaplatform.clients.ClientLookup;
import br.com.agendaplatform.clients.ClientRef;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.scheduling.domain.Appointment;
import br.com.agendaplatform.scheduling.domain.AppointmentConflictException;
import br.com.agendaplatform.scheduling.domain.UnknownReferenceException;
import br.com.agendaplatform.scheduling.infrastructure.AppointmentRepository;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentScheduler {

    private final AppointmentRepository appointmentRepository;
    private final ClientLookup clientLookup;
    private final ServiceLookup serviceLookup;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;

    AppointmentScheduler(
            AppointmentRepository appointmentRepository,
            ClientLookup clientLookup,
            ServiceLookup serviceLookup,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder) {
        this.appointmentRepository = appointmentRepository;
        this.clientLookup = clientLookup;
        this.serviceLookup = serviceLookup;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public AppointmentSummary create(UUID clientId, UUID serviceId, Instant startAt, Instant endAt) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        ClientRef client = clientLookup
                .find(clientId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Cliente não encontrada."));
        ServiceRef service = serviceLookup
                .find(serviceId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Serviço não encontrado."));

        Appointment appointment = new Appointment(organizationId, clientId, serviceId, startAt, endAt);

        if (appointmentRepository.existsOverlapping(organizationId, startAt, endAt)) {
            throw new AppointmentConflictException("Já existe um agendamento nesse horário.");
        }

        appointmentRepository.save(appointment);

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "APPOINTMENT_CREATED",
                "APPOINTMENT",
                appointment.getId());

        return new AppointmentSummary(
                appointment.getId(), client.name(), service.name(), appointment.getStartAt(), appointment.getEndAt());
    }

    @Transactional(readOnly = true)
    public List<AppointmentSummary> list() {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return appointmentRepository.findAllByOrganizationIdOrderByStartAtAsc(organizationId).stream()
                .map(appointment -> toSummary(appointment, organizationId))
                .toList();
    }

    private AppointmentSummary toSummary(Appointment appointment, UUID organizationId) {
        String clientName = clientLookup
                .find(appointment.getClientId(), organizationId)
                .map(ClientRef::name)
                .orElse("Cliente não encontrada");
        String serviceName = serviceLookup
                .find(appointment.getServiceId(), organizationId)
                .map(ServiceRef::name)
                .orElse("Serviço não encontrado");
        return new AppointmentSummary(
                appointment.getId(), clientName, serviceName, appointment.getStartAt(), appointment.getEndAt());
    }
}
