package br.com.agendaplatform.waitlist.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ConvertWaitlistEntryRequest(@NotNull Instant startAt, @NotNull Instant endAt) {
}
