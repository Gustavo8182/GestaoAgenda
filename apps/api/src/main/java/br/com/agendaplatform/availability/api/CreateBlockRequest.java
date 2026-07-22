package br.com.agendaplatform.availability.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateBlockRequest(@NotNull Instant startAt, @NotNull Instant endAt, @NotBlank String reason) {
}
