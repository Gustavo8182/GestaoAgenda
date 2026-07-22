package br.com.agendaplatform.scheduling.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleAppointmentRequest(@NotNull Instant startAt, @NotNull Instant endAt) {
}
