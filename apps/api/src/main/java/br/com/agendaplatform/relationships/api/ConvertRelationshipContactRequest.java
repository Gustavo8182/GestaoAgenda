package br.com.agendaplatform.relationships.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ConvertRelationshipContactRequest(@NotNull UUID serviceId, @NotNull Instant startAt, @NotNull Instant endAt) {
}
