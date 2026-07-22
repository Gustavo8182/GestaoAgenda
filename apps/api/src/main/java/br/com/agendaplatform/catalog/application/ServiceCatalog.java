package br.com.agendaplatform.catalog.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.catalog.domain.Service;
import br.com.agendaplatform.catalog.infrastructure.ServiceRepository;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
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

    ServiceCatalog(
            ServiceRepository serviceRepository,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder) {
        this.serviceRepository = serviceRepository;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public ServiceSummary create(String name, int durationMinutes) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        Service service = new Service(organizationId, name, durationMinutes);
        serviceRepository.save(service);

        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), "SERVICE_CREATED", "SERVICE", service.getId());

        return ServiceSummary.from(service);
    }

    @Transactional(readOnly = true)
    public List<ServiceSummary> list() {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return serviceRepository.findAllByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(ServiceSummary::from)
                .toList();
    }
}
