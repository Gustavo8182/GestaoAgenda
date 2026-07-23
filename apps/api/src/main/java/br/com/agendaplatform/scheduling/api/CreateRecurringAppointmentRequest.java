package br.com.agendaplatform.scheduling.api;

import br.com.agendaplatform.scheduling.domain.RecurrenceFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateRecurringAppointmentRequest(
        @NotNull UUID clientId,
        @NotNull UUID serviceId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotNull RecurrenceFrequency frequency,
        @Min(2) @Max(52) int occurrenceCount) {
}
