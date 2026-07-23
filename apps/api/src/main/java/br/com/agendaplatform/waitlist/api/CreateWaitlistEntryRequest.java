package br.com.agendaplatform.waitlist.api;

import br.com.agendaplatform.waitlist.domain.WaitlistPriority;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateWaitlistEntryRequest(
        @NotNull UUID clientId,
        @NotNull UUID serviceId,
        @NotNull LocalDate preferredStartDate,
        @NotNull LocalDate preferredEndDate,
        @NotNull LocalTime preferredStartTime,
        @NotNull LocalTime preferredEndTime,
        @NotNull WaitlistPriority priority,
        @NotNull Instant expiresAt) {
}
