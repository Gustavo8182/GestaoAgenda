package br.com.agendaplatform.scheduling.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.availability.AvailabilityCheck;
import br.com.agendaplatform.catalog.ServiceLookup;
import br.com.agendaplatform.catalog.ServiceRef;
import br.com.agendaplatform.clients.ClientLookup;
import br.com.agendaplatform.clients.ClientRef;
import br.com.agendaplatform.organizations.CurrentOrganization;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.scheduling.domain.Appointment;
import br.com.agendaplatform.scheduling.domain.AppointmentConflictException;
import br.com.agendaplatform.scheduling.domain.AppointmentNotFoundException;
import br.com.agendaplatform.scheduling.domain.BlockedTimeException;
import br.com.agendaplatform.scheduling.domain.OutsideBusinessHoursException;
import br.com.agendaplatform.scheduling.domain.UnknownReferenceException;
import br.com.agendaplatform.scheduling.infrastructure.AppointmentRepository;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final AvailabilityCheck availabilityCheck;

    AppointmentScheduler(
            AppointmentRepository appointmentRepository,
            ClientLookup clientLookup,
            ServiceLookup serviceLookup,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder,
            AvailabilityCheck availabilityCheck) {
        this.appointmentRepository = appointmentRepository;
        this.clientLookup = clientLookup;
        this.serviceLookup = serviceLookup;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
        this.availabilityCheck = availabilityCheck;
    }

    @Transactional
    public AppointmentSummary create(UUID clientId, UUID serviceId, Instant startAt, Instant endAt) {
        CurrentOrganization organization = currentOrganizationProvider.current();
        UUID organizationId = organization.organizationId();
        String timezone = organization.timezone();

        ClientRef client = clientLookup
                .find(clientId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Cliente não encontrada."));
        ServiceRef service = serviceLookup
                .find(serviceId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Serviço não encontrado."));

        Appointment appointment = new Appointment(organizationId, clientId, serviceId, startAt, endAt);

        checkAvailability(organizationId, timezone, startAt, endAt);

        if (appointmentRepository.existsOverlappingExcluding(organizationId, startAt, endAt, appointment.getId())) {
            throw new AppointmentConflictException("Já existe um agendamento nesse horário.");
        }

        appointmentRepository.save(appointment);

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "APPOINTMENT_CREATED",
                "APPOINTMENT",
                appointment.getId());

        return toSummary(appointment, client.name(), service.name());
    }

    @Transactional
    public AppointmentSummary reschedule(UUID appointmentId, Instant newStartAt, Instant newEndAt) {
        CurrentOrganization organization = currentOrganizationProvider.current();
        UUID organizationId = organization.organizationId();
        String timezone = organization.timezone();
        Appointment appointment = findOrThrow(appointmentId, organizationId);

        Instant previousStartAt = appointment.getStartAt();
        Instant previousEndAt = appointment.getEndAt();

        appointment.reschedule(newStartAt, newEndAt);

        checkAvailability(organizationId, timezone, newStartAt, newEndAt);

        if (appointmentRepository.existsOverlappingExcluding(
                organizationId, newStartAt, newEndAt, appointment.getId())) {
            throw new AppointmentConflictException("Já existe um agendamento nesse horário.");
        }

        appointmentRepository.save(appointment);

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "APPOINTMENT_RESCHEDULED",
                "APPOINTMENT",
                appointment.getId(),
                Map.of(
                        "previousStartAt", previousStartAt.toString(),
                        "previousEndAt", previousEndAt.toString(),
                        "newStartAt", newStartAt.toString(),
                        "newEndAt", newEndAt.toString()));

        return toSummary(appointment, organizationId);
    }

    @Transactional
    public AppointmentSummary cancel(UUID appointmentId, String reason) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        Appointment appointment = findOrThrow(appointmentId, organizationId);

        appointment.cancel(reason);
        appointmentRepository.save(appointment);

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "APPOINTMENT_CANCELLED",
                "APPOINTMENT",
                appointment.getId(),
                Map.of("reason", reason));

        return toSummary(appointment, organizationId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentSummary> list() {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return appointmentRepository.findAllByOrganizationIdOrderByStartAtAsc(organizationId).stream()
                .map(appointment -> toSummary(appointment, organizationId))
                .toList();
    }

    private void checkAvailability(UUID organizationId, String timezone, Instant startAt, Instant endAt) {
        if (!availabilityCheck.isWithinBusinessHours(organizationId, timezone, startAt, endAt)) {
            throw new OutsideBusinessHoursException("Horário fora do funcionamento da organização.");
        }
        if (availabilityCheck.overlapsBlock(organizationId, startAt, endAt)) {
            throw new BlockedTimeException("Este horário está bloqueado.");
        }
    }

    private Appointment findOrThrow(UUID appointmentId, UUID organizationId) {
        return appointmentRepository
                .findByIdAndOrganizationId(appointmentId, organizationId)
                .orElseThrow(() -> new AppointmentNotFoundException("Agendamento não encontrado."));
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
        return toSummary(appointment, clientName, serviceName);
    }

    private AppointmentSummary toSummary(Appointment appointment, String clientName, String serviceName) {
        return new AppointmentSummary(
                appointment.getId(),
                clientName,
                serviceName,
                appointment.getStartAt(),
                appointment.getEndAt(),
                appointment.getStatus().name(),
                appointment.getCancellationReason());
    }
}
