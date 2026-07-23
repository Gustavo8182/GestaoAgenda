package br.com.agendaplatform.catalog.infrastructure;

import br.com.agendaplatform.catalog.ServiceLookup;
import br.com.agendaplatform.catalog.ServiceRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RepositoryServiceLookup implements ServiceLookup {

    private final ServiceRepository serviceRepository;

    RepositoryServiceLookup(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Override
    public Optional<ServiceRef> find(UUID serviceId, UUID organizationId) {
        return serviceRepository
                .findByIdAndOrganizationId(serviceId, organizationId)
                .map(service -> new ServiceRef(
                        service.getId(), service.getName(), service.getDurationMinutes(), service.getBufferMinutes()));
    }
}
