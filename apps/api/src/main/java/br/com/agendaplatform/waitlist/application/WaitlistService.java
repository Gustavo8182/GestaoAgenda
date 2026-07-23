package br.com.agendaplatform.waitlist.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.clients.ClientLookup;
import br.com.agendaplatform.clients.ClientRef;
import br.com.agendaplatform.catalog.ServiceLookup;
import br.com.agendaplatform.catalog.ServiceRef;
import br.com.agendaplatform.organizations.CurrentOrganization;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.scheduling.AppointmentBooking;
import br.com.agendaplatform.scheduling.AppointmentSummary;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import br.com.agendaplatform.waitlist.domain.InvalidWaitlistEntryException;
import br.com.agendaplatform.waitlist.domain.UnknownReferenceException;
import br.com.agendaplatform.waitlist.domain.WaitlistEntry;
import br.com.agendaplatform.waitlist.domain.WaitlistEntryNotFoundException;
import br.com.agendaplatform.waitlist.WaitlistExportRow;
import br.com.agendaplatform.waitlist.WaitlistOverview;
import br.com.agendaplatform.waitlist.domain.WaitlistPriority;
import br.com.agendaplatform.waitlist.infrastructure.WaitlistEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitlistService implements WaitlistOverview {

    private static final Comparator<WaitlistEntry> LIST_ORDER = Comparator
            .comparing((WaitlistEntry entry) -> entry.getPriority().ordinal())
            .reversed()
            .thenComparing(WaitlistEntry::getCreatedAt);

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final ClientLookup clientLookup;
    private final ServiceLookup serviceLookup;
    private final AppointmentBooking appointmentBooking;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    WaitlistService(
            WaitlistEntryRepository waitlistEntryRepository,
            ClientLookup clientLookup,
            ServiceLookup serviceLookup,
            AppointmentBooking appointmentBooking,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.clientLookup = clientLookup;
        this.serviceLookup = serviceLookup;
        this.appointmentBooking = appointmentBooking;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public WaitlistSummary create(
            UUID clientId,
            UUID serviceId,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            WaitlistPriority priority,
            Instant expiresAt) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        ClientRef client = clientLookup
                .find(clientId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Cliente não encontrada."));
        ServiceRef service = serviceLookup
                .find(serviceId, organizationId)
                .orElseThrow(() -> new UnknownReferenceException("Serviço não encontrado."));

        if (expiresAt.isBefore(clock.instant())) {
            throw new InvalidWaitlistEntryException("A validade deve ser no futuro.");
        }

        WaitlistEntry entry = new WaitlistEntry(
                organizationId,
                clientId,
                serviceId,
                preferredStartDate,
                preferredEndDate,
                preferredStartTime,
                preferredEndTime,
                priority,
                expiresAt);
        waitlistEntryRepository.save(entry);

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "WAITLIST_ENTRY_CREATED",
                "WAITLIST_ENTRY",
                entry.getId());

        return toSummary(entry, client.name(), service.name());
    }

    @Transactional(readOnly = true)
    public List<WaitlistSummary> list() {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return waitlistEntryRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .sorted(LIST_ORDER)
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public WaitlistSummary cancel(UUID entryId) {
        WaitlistEntry entry = findOrThrow(entryId);
        entry.cancel();
        waitlistEntryRepository.save(entry);

        auditRecorder.record(
                entry.getOrganizationId(),
                currentActorProvider.currentUserId(),
                "WAITLIST_ENTRY_CANCELLED",
                "WAITLIST_ENTRY",
                entry.getId());

        return toSummary(entry);
    }

    @Transactional
    public AppointmentSummary convert(UUID entryId, Instant startAt, Instant endAt) {
        WaitlistEntry entry = findOrThrow(entryId);

        AppointmentSummary appointment =
                appointmentBooking.create(entry.getClientId(), entry.getServiceId(), startAt, endAt);

        entry.convert(clock.instant(), appointment.id());
        waitlistEntryRepository.save(entry);

        auditRecorder.record(
                entry.getOrganizationId(),
                currentActorProvider.currentUserId(),
                "WAITLIST_ENTRY_CONVERTED",
                "WAITLIST_ENTRY",
                entry.getId(),
                Map.of("appointmentId", appointment.id().toString()));

        return appointment;
    }

    @Transactional(readOnly = true)
    public List<WaitlistSummary> findCompatible(UUID serviceId, Instant startAt, Instant endAt) {
        CurrentOrganization organization = currentOrganizationProvider.current();
        UUID organizationId = organization.organizationId();
        ZoneId zoneId = ZoneId.of(organization.timezone());

        ZonedDateTime start = startAt.atZone(zoneId);
        ZonedDateTime end = endAt.atZone(zoneId);
        Instant now = clock.instant();

        return waitlistEntryRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .filter(entry -> entry.matchesSlot(serviceId, now, start.toLocalDate(), start.toLocalTime(), end.toLocalTime()))
                .sorted(LIST_ORDER)
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WaitlistExportRow> findAll(UUID organizationId) {
        Instant now = clock.instant();
        return waitlistEntryRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .sorted(LIST_ORDER)
                .map(entry -> new WaitlistExportRow(
                        clientLookup.find(entry.getClientId(), organizationId).map(ClientRef::name).orElse("Cliente não encontrada"),
                        serviceLookup.find(entry.getServiceId(), organizationId).map(ServiceRef::name).orElse("Serviço não encontrado"),
                        entry.getPreferredStartDate(),
                        entry.getPreferredEndDate(),
                        entry.getPreferredStartTime(),
                        entry.getPreferredEndTime(),
                        entry.getPriority().name(),
                        entry.getExpiresAt(),
                        entry.isExpired(now) ? "EXPIRED" : entry.getStatus().name()))
                .toList();
    }

    private WaitlistEntry findOrThrow(UUID entryId) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return waitlistEntryRepository
                .findByIdAndOrganizationId(entryId, organizationId)
                .orElseThrow(() -> new WaitlistEntryNotFoundException("Registro da lista de espera não encontrado."));
    }

    private WaitlistSummary toSummary(WaitlistEntry entry) {
        UUID organizationId = entry.getOrganizationId();
        String clientName = clientLookup
                .find(entry.getClientId(), organizationId)
                .map(ClientRef::name)
                .orElse("Cliente não encontrada");
        String serviceName = serviceLookup
                .find(entry.getServiceId(), organizationId)
                .map(ServiceRef::name)
                .orElse("Serviço não encontrado");
        return toSummary(entry, clientName, serviceName);
    }

    private WaitlistSummary toSummary(WaitlistEntry entry, String clientName, String serviceName) {
        String effectiveStatus =
                entry.isExpired(clock.instant()) ? "EXPIRED" : entry.getStatus().name();
        return new WaitlistSummary(
                entry.getId(),
                entry.getServiceId(),
                clientName,
                serviceName,
                entry.getPreferredStartDate(),
                entry.getPreferredEndDate(),
                entry.getPreferredStartTime(),
                entry.getPreferredEndTime(),
                entry.getPriority().name(),
                entry.getExpiresAt(),
                effectiveStatus,
                entry.getAppointmentId());
    }
}
