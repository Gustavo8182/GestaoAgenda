package br.com.agendaplatform.catalog.application;

import br.com.agendaplatform.catalog.domain.Service;
import java.util.UUID;

public record ServiceSummary(
        UUID id,
        String name,
        int durationMinutes,
        String color,
        int displayOrder,
        boolean requiresConfirmation,
        boolean active) {

    static ServiceSummary from(Service service) {
        return new ServiceSummary(
                service.getId(),
                service.getName(),
                service.getDurationMinutes(),
                service.getColor(),
                service.getDisplayOrder(),
                service.isRequiresConfirmation(),
                service.isActive());
    }
}
