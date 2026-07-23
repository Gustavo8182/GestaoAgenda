package br.com.agendaplatform.scheduling.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EditAppointmentRequest(@NotNull UUID clientId, @NotNull UUID serviceId) {
}
