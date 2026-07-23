package br.com.agendaplatform.scheduling.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.availability.AvailabilityCheck;
import br.com.agendaplatform.catalog.ServiceLookup;
import br.com.agendaplatform.catalog.ServiceRef;
import br.com.agendaplatform.clients.ClientLookup;
import br.com.agendaplatform.clients.ClientRef;
import br.com.agendaplatform.organizations.CurrentOrganization;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.OrganizationAccessGuard;
import br.com.agendaplatform.scheduling.AppointmentBooking;
import br.com.agendaplatform.scheduling.AppointmentOverview;
import br.com.agendaplatform.scheduling.AppointmentSummary;
import br.com.agendaplatform.scheduling.domain.Appointment;
import br.com.agendaplatform.scheduling.domain.AppointmentConflictException;
import br.com.agendaplatform.scheduling.domain.AppointmentNotFoundException;
import br.com.agendaplatform.scheduling.domain.AppointmentStatus;
import br.com.agendaplatform.scheduling.domain.BlockedTimeException;
import br.com.agendaplatform.scheduling.domain.OutsideBusinessHoursException;
import br.com.agendaplatform.scheduling.domain.RecurrenceFrequency;
import br.com.agendaplatform.scheduling.domain.UnknownReferenceException;
import br.com.agendaplatform.scheduling.infrastructure.AppointmentRepository;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentScheduler implements AppointmentOverview, AppointmentBooking {

    private final AppointmentRepository appointmentRepository;
    private final ClientLookup clientLookup;
    private final ServiceLookup serviceLookup;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;
    private final AvailabilityCheck availabilityCheck;
    private final OrganizationAccessGuard organizationAccessGuard;

    AppointmentScheduler(
            AppointmentRepository appointmentRepository,
            ClientLookup clientLookup,
            ServiceLookup serviceLookup,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder,
            AvailabilityCheck availabilityCheck,
            OrganizationAccessGuard organizationAccessGuard) {
        this.appointmentRepository = appointmentRepository;
        this.clientLookup = clientLookup;
        this.serviceLookup = serviceLookup;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
        this.availabilityCheck = availabilityCheck;
        this.organizationAccessGuard = organizationAccessGuard;
    }

    @Override
    @Transactional
    public AppointmentSummary create(UUID clientId, UUID serviceId, Instant startAt, Instant endAt) {
        organizationAccessGuard.requireOperator();
        CurrentOrganization organization = currentOrganizationProvider.current();
        UUID organizationId = organization.organizationId();
        String timezone = organization.timezone();

        ClientRef client = clientLookup
                .find(clientId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Cliente não encontrada."));
        ServiceRef service = serviceLookup
                .find(serviceId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Serviço não encontrado."));

        Appointment appointment = createAndSaveAppointment(
                organizationId, timezone, clientId, serviceId, startAt, endAt, service.bufferMinutes(), null, Map.of());

        return toSummary(appointment, client.name(), service.name());
    }

    @Transactional
    public List<AppointmentSummary> createRecurring(
            UUID clientId,
            UUID serviceId,
            Instant firstStartAt,
            Instant firstEndAt,
            RecurrenceFrequency frequency,
            int occurrenceCount) {
        organizationAccessGuard.requireOperator();
        CurrentOrganization organization = currentOrganizationProvider.current();
        UUID organizationId = organization.organizationId();
        String timezone = organization.timezone();

        ClientRef client = clientLookup
                .find(clientId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Cliente não encontrada."));
        ServiceRef service = serviceLookup
                .find(serviceId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Serviço não encontrado."));

        UUID seriesId = UUID.randomUUID();
        Duration occurrenceDuration = Duration.between(firstStartAt, firstEndAt);
        Duration interval = Duration.ofDays(frequency.intervalDays());

        List<AppointmentSummary> occurrences = new ArrayList<>();
        for (int index = 0; index < occurrenceCount; index++) {
            Instant occurrenceStartAt = firstStartAt.plus(interval.multipliedBy(index));
            Instant occurrenceEndAt = occurrenceStartAt.plus(occurrenceDuration);

            Appointment appointment = createAndSaveAppointment(
                    organizationId,
                    timezone,
                    clientId,
                    serviceId,
                    occurrenceStartAt,
                    occurrenceEndAt,
                    service.bufferMinutes(),
                    seriesId,
                    Map.of("seriesId", seriesId.toString(), "occurrenceIndex", String.valueOf(index)));

            occurrences.add(toSummary(appointment, client.name(), service.name()));
        }

        return occurrences;
    }

    private Appointment createAndSaveAppointment(
            UUID organizationId,
            String timezone,
            UUID clientId,
            UUID serviceId,
            Instant startAt,
            Instant endAt,
            int bufferMinutes,
            UUID seriesId,
            Map<String, String> auditMetadata) {
        Appointment appointment =
                new Appointment(organizationId, clientId, serviceId, startAt, endAt, bufferMinutes, seriesId);

        checkAvailability(organizationId, timezone, startAt, endAt);

        Instant effectiveEndAt = endAt.plus(Duration.ofMinutes(bufferMinutes));
        if (appointmentRepository.existsOverlappingExcluding(
                organizationId, startAt, effectiveEndAt, appointment.getId())) {
            throw new AppointmentConflictException("Já existe um agendamento nesse horário.");
        }

        appointmentRepository.save(appointment);

        if (auditMetadata.isEmpty()) {
            auditRecorder.record(
                    organizationId,
                    currentActorProvider.currentUserId(),
                    "APPOINTMENT_CREATED",
                    "APPOINTMENT",
                    appointment.getId());
        } else {
            auditRecorder.record(
                    organizationId,
                    currentActorProvider.currentUserId(),
                    "APPOINTMENT_CREATED",
                    "APPOINTMENT",
                    appointment.getId(),
                    auditMetadata);
        }

        return appointment;
    }

    @Transactional
    public AppointmentSummary reschedule(UUID appointmentId, Instant newStartAt, Instant newEndAt) {
        organizationAccessGuard.requireOperator();
        CurrentOrganization organization = currentOrganizationProvider.current();
        UUID organizationId = organization.organizationId();
        String timezone = organization.timezone();
        Appointment appointment = findOrThrow(appointmentId, organizationId);

        Instant previousStartAt = appointment.getStartAt();
        Instant previousEndAt = appointment.getEndAt();

        appointment.reschedule(newStartAt, newEndAt);

        checkAvailability(organizationId, timezone, newStartAt, newEndAt);

        Instant effectiveEndAt = newEndAt.plus(Duration.ofMinutes(appointment.getBufferMinutes()));
        if (appointmentRepository.existsOverlappingExcluding(
                organizationId, newStartAt, effectiveEndAt, appointment.getId())) {
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
    public AppointmentSummary edit(UUID appointmentId, UUID newClientId, UUID newServiceId) {
        organizationAccessGuard.requireOperator();
        CurrentOrganization organization = currentOrganizationProvider.current();
        UUID organizationId = organization.organizationId();
        String timezone = organization.timezone();
        Appointment appointment = findOrThrow(appointmentId, organizationId);

        ClientRef client = clientLookup
                .find(newClientId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Cliente não encontrada."));
        ServiceRef service = serviceLookup
                .find(newServiceId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Serviço não encontrado."));

        UUID previousClientId = appointment.getClientId();
        UUID previousServiceId = appointment.getServiceId();
        Instant newEndAt = appointment.getStartAt().plus(Duration.ofMinutes(service.durationMinutes()));

        appointment.edit(newClientId, newServiceId, newEndAt, service.bufferMinutes());

        checkAvailability(organizationId, timezone, appointment.getStartAt(), newEndAt);

        Instant effectiveEndAt = newEndAt.plus(Duration.ofMinutes(service.bufferMinutes()));
        if (appointmentRepository.existsOverlappingExcluding(
                organizationId, appointment.getStartAt(), effectiveEndAt, appointment.getId())) {
            throw new AppointmentConflictException("Já existe um agendamento nesse horário.");
        }

        appointmentRepository.save(appointment);

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "APPOINTMENT_EDITED",
                "APPOINTMENT",
                appointment.getId(),
                Map.of(
                        "previousClientId", previousClientId.toString(),
                        "previousServiceId", previousServiceId.toString(),
                        "newClientId", newClientId.toString(),
                        "newServiceId", newServiceId.toString()));

        return toSummary(appointment, client.name(), service.name());
    }

    @Transactional
    public AppointmentSummary cancel(UUID appointmentId, String reason) {
        organizationAccessGuard.requireOperator();
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
        organizationAccessGuard.requireOperator();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return appointmentRepository.findAllByOrganizationIdOrderByStartAtAsc(organizationId).stream()
                .map(appointment -> toSummary(appointment, organizationId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentSummary> listByClient(UUID clientId) {
        organizationAccessGuard.requireOperator();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return appointmentRepository.findAllByOrganizationIdAndClientIdOrderByStartAtDesc(organizationId, clientId).stream()
                .map(appointment -> toSummary(appointment, organizationId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentSummary> findByStartAtBetween(
            UUID organizationId, Instant startInclusive, Instant endExclusive) {
        return appointmentRepository
                .findAllByOrganizationIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        organizationId, startInclusive, endExclusive)
                .stream()
                .map(appointment -> toSummary(appointment, organizationId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentSummary> findAll(UUID organizationId) {
        return appointmentRepository.findAllByOrganizationIdOrderByStartAtAsc(organizationId).stream()
                .map(appointment -> toSummary(appointment, organizationId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppointmentSummary> findNextUpcoming(UUID organizationId, Instant from) {
        List<AppointmentStatus> excluded =
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW, AppointmentStatus.DONE);
        return appointmentRepository
                .findFirstByOrganizationIdAndStatusNotInAndStartAtGreaterThanEqualOrderByStartAtAsc(
                        organizationId, excluded, from)
                .map(appointment -> toSummary(appointment, organizationId));
    }

    @Transactional
    public AppointmentSummary confirm(UUID appointmentId) {
        return applyTransition(appointmentId, Appointment::confirm, "APPOINTMENT_CONFIRMED");
    }

    @Transactional
    public AppointmentSummary registerArrival(UUID appointmentId) {
        return applyTransition(appointmentId, Appointment::registerArrival, "APPOINTMENT_ARRIVED");
    }

    @Transactional
    public AppointmentSummary startService(UUID appointmentId) {
        return applyTransition(appointmentId, Appointment::startService, "APPOINTMENT_STARTED");
    }

    @Transactional
    public AppointmentSummary complete(UUID appointmentId) {
        return applyTransition(appointmentId, Appointment::complete, "APPOINTMENT_COMPLETED");
    }

    @Transactional
    public AppointmentSummary markNoShow(UUID appointmentId) {
        return applyTransition(appointmentId, Appointment::markNoShow, "APPOINTMENT_NO_SHOW");
    }

    private AppointmentSummary applyTransition(UUID appointmentId, Consumer<Appointment> transition, String action) {
        organizationAccessGuard.requireOperator();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        Appointment appointment = findOrThrow(appointmentId, organizationId);

        transition.accept(appointment);
        appointmentRepository.save(appointment);

        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), action, "APPOINTMENT", appointment.getId());

        return toSummary(appointment, organizationId);
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
                appointment.getClientId(),
                clientName,
                appointment.getServiceId(),
                serviceName,
                appointment.getStartAt(),
                appointment.getEndAt(),
                appointment.getStatus().name(),
                appointment.getCancellationReason(),
                appointment.getSeriesId());
    }
}
