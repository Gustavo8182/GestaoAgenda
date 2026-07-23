package br.com.agendaplatform.catalog.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.catalog.domain.Service;
import br.com.agendaplatform.catalog.domain.ServiceNotFoundException;
import br.com.agendaplatform.catalog.infrastructure.ServiceRepository;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.OrganizationAccessGuard;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class ServiceCatalog {

    private final ServiceRepository serviceRepository;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;
    private final OrganizationAccessGuard organizationAccessGuard;

    ServiceCatalog(
            ServiceRepository serviceRepository,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder,
            OrganizationAccessGuard organizationAccessGuard) {
        this.serviceRepository = serviceRepository;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
        this.organizationAccessGuard = organizationAccessGuard;
    }

    @Transactional
    public ServiceSummary create(
            String name,
            int durationMinutes,
            String color,
            Integer displayOrder,
            boolean requiresConfirmation,
            Integer bufferMinutes) {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        int resolvedOrder =
                displayOrder != null ? displayOrder : serviceRepository.nextDisplayOrder(organizationId);

        Service service = new Service(
                organizationId,
                name,
                durationMinutes,
                color,
                resolvedOrder,
                requiresConfirmation,
                bufferMinutes != null ? bufferMinutes : 0);
        serviceRepository.save(service);

        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), "SERVICE_CREATED", "SERVICE", service.getId());

        return ServiceSummary.from(service);
    }

    @Transactional
    public ServiceSummary edit(
            UUID serviceId,
            String name,
            int durationMinutes,
            String color,
            int displayOrder,
            boolean requiresConfirmation,
            Integer bufferMinutes) {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        Service service = serviceRepository
                .findByIdAndOrganizationId(serviceId, organizationId)
                .orElseThrow(() -> new ServiceNotFoundException("Serviço não encontrado."));

        service.edit(
                name, durationMinutes, color, displayOrder, requiresConfirmation, bufferMinutes != null ? bufferMinutes : 0);

        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), "SERVICE_EDITED", "SERVICE", service.getId());

        return ServiceSummary.from(service);
    }

    @Transactional
    public ServiceSummary deactivate(UUID serviceId) {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        Service service = serviceRepository
                .findByIdAndOrganizationId(serviceId, organizationId)
                .orElseThrow(() -> new ServiceNotFoundException("Serviço não encontrado."));

        service.deactivate();

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "SERVICE_DEACTIVATED",
                "SERVICE",
                service.getId());

        return ServiceSummary.from(service);
    }

    @Transactional
    public ServiceSummary reactivate(UUID serviceId) {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        Service service = serviceRepository
                .findByIdAndOrganizationId(serviceId, organizationId)
                .orElseThrow(() -> new ServiceNotFoundException("Serviço não encontrado."));

        service.reactivate();

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "SERVICE_REACTIVATED",
                "SERVICE",
                service.getId());

        return ServiceSummary.from(service);
    }

    @Transactional(readOnly = true)
    public List<ServiceSummary> list() {
        organizationAccessGuard.requireOperator();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return serviceRepository.findAllByOrganizationIdOrderByDisplayOrderAscNameAsc(organizationId).stream()
                .map(ServiceSummary::from)
                .toList();
    }
}
