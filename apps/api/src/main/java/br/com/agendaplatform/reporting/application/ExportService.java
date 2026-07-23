package br.com.agendaplatform.reporting.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.clients.ClientExportRow;
import br.com.agendaplatform.clients.ClientLookup;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.OrganizationAccessGuard;
import br.com.agendaplatform.relationships.RelationshipExportRow;
import br.com.agendaplatform.relationships.RelationshipOverview;
import br.com.agendaplatform.scheduling.AppointmentOverview;
import br.com.agendaplatform.scheduling.AppointmentSummary;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import br.com.agendaplatform.shared.web.CsvWriter;
import br.com.agendaplatform.waitlist.WaitlistExportRow;
import br.com.agendaplatform.waitlist.WaitlistOverview;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {

    private final ClientLookup clientLookup;
    private final AppointmentOverview appointmentOverview;
    private final WaitlistOverview waitlistOverview;
    private final RelationshipOverview relationshipOverview;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;
    private final OrganizationAccessGuard organizationAccessGuard;

    ExportService(
            ClientLookup clientLookup,
            AppointmentOverview appointmentOverview,
            WaitlistOverview waitlistOverview,
            RelationshipOverview relationshipOverview,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder,
            OrganizationAccessGuard organizationAccessGuard) {
        this.clientLookup = clientLookup;
        this.appointmentOverview = appointmentOverview;
        this.waitlistOverview = waitlistOverview;
        this.relationshipOverview = relationshipOverview;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
        this.organizationAccessGuard = organizationAccessGuard;
    }

    @Transactional
    public String exportClients() {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        List<ClientExportRow> rows = clientLookup.listAll(organizationId);

        String csv = CsvWriter.toCsv(
                List.of("Nome", "Telefone", "Telefone alternativo", "Origem", "Observações"),
                rows.stream()
                        .map(row -> List.of(
                                row.name(),
                                row.phone(),
                                nullToEmpty(row.alternatePhone()),
                                nullToEmpty(row.origin()),
                                nullToEmpty(row.notes())))
                        .toList());

        recordExport(organizationId, "CLIENTS_EXPORTED");
        return csv;
    }

    @Transactional
    public String exportAppointments() {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        List<AppointmentSummary> rows = appointmentOverview.findAll(organizationId);

        String csv = CsvWriter.toCsv(
                List.of("Cliente", "Serviço", "Início", "Fim", "Status", "Motivo do cancelamento"),
                rows.stream()
                        .map(row -> List.of(
                                row.clientName(),
                                row.serviceName(),
                                row.startAt().toString(),
                                row.endAt().toString(),
                                row.status(),
                                nullToEmpty(row.cancellationReason())))
                        .toList());

        recordExport(organizationId, "APPOINTMENTS_EXPORTED");
        return csv;
    }

    @Transactional
    public String exportWaitlist() {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        List<WaitlistExportRow> rows = waitlistOverview.findAll(organizationId);

        String csv = CsvWriter.toCsv(
                List.of(
                        "Cliente",
                        "Serviço",
                        "Período preferido - de",
                        "Período preferido - até",
                        "Horário preferido - de",
                        "Horário preferido - até",
                        "Prioridade",
                        "Válido até",
                        "Situação"),
                rows.stream()
                        .map(row -> List.of(
                                row.clientName(),
                                row.serviceName(),
                                row.preferredStartDate().toString(),
                                row.preferredEndDate().toString(),
                                row.preferredStartTime().toString(),
                                row.preferredEndTime().toString(),
                                row.priority(),
                                row.expiresAt().toString(),
                                row.status()))
                        .toList());

        recordExport(organizationId, "WAITLIST_EXPORTED");
        return csv;
    }

    @Transactional
    public String exportRelationships() {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        List<RelationshipExportRow> rows = relationshipOverview.findAll(organizationId);

        String csv = CsvWriter.toCsv(
                List.of(
                        "Nome",
                        "Telefone",
                        "Origem",
                        "Situação",
                        "Última interação",
                        "Próxima ação",
                        "Data da próxima ação",
                        "Responsável"),
                rows.stream()
                        .map(row -> List.of(
                                row.name(),
                                row.phone(),
                                nullToEmpty(row.origin()),
                                row.status(),
                                row.lastInteractionAt().toString(),
                                nullToEmpty(row.nextAction()),
                                row.nextActionAt() == null ? "" : row.nextActionAt().toString(),
                                row.responsibleName()))
                        .toList());

        recordExport(organizationId, "RELATIONSHIPS_EXPORTED");
        return csv;
    }

    private void recordExport(UUID organizationId, String action) {
        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), action, "EXPORT", organizationId);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
