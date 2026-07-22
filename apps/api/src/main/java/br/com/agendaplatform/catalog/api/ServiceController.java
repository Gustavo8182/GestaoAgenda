package br.com.agendaplatform.catalog.api;

import br.com.agendaplatform.catalog.application.ServiceCatalog;
import br.com.agendaplatform.catalog.application.ServiceSummary;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
        return serviceCatalog.create(request.name(), request.durationMinutes());
    }

    @GetMapping
    List<ServiceSummary> list() {
        return serviceCatalog.list();
    }
}
