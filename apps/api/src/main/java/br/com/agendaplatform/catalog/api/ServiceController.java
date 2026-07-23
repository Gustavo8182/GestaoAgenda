package br.com.agendaplatform.catalog.api;

import br.com.agendaplatform.catalog.application.ServiceCatalog;
import br.com.agendaplatform.catalog.application.ServiceSummary;
import br.com.agendaplatform.catalog.domain.ServiceNotFoundException;
import br.com.agendaplatform.shared.web.ErrorResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/services")
class ServiceController {

    private final ServiceCatalog serviceCatalog;

    ServiceController(ServiceCatalog serviceCatalog) {
        this.serviceCatalog = serviceCatalog;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ServiceSummary create(@Valid @RequestBody CreateServiceRequest request) {
        return serviceCatalog.create(
                request.name(),
                request.durationMinutes(),
                request.color(),
                request.displayOrder(),
                request.requiresConfirmationOrDefault(),
                request.bufferMinutes());
    }

    @GetMapping
    List<ServiceSummary> list() {
        return serviceCatalog.list();
    }

    @PostMapping("/{serviceId}/edit")
    ServiceSummary edit(@PathVariable UUID serviceId, @Valid @RequestBody EditServiceRequest request) {
        return serviceCatalog.edit(
                serviceId,
                request.name(),
                request.durationMinutes(),
                request.color(),
                request.displayOrder(),
                request.requiresConfirmationOrDefault(),
                request.bufferMinutes());
    }

    @PostMapping("/{serviceId}/deactivate")
    ServiceSummary deactivate(@PathVariable UUID serviceId) {
        return serviceCatalog.deactivate(serviceId);
    }

    @PostMapping("/{serviceId}/reactivate")
    ServiceSummary reactivate(@PathVariable UUID serviceId) {
        return serviceCatalog.reactivate(serviceId);
    }

    @ExceptionHandler(ServiceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNotFound(ServiceNotFoundException exception) {
        return new ErrorResponse("service_not_found", exception.getMessage());
    }
}
