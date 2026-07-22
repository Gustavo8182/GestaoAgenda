package br.com.agendaplatform.scheduling.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID clientId, @NotNull UUID serviceId, @NotNull Instant startAt, @NotNull Instant endAt) {
}
