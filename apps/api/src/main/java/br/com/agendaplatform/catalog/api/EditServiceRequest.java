package br.com.agendaplatform.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record EditServiceRequest(
        @NotBlank String name,
        @Positive int durationMinutes,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB.") String color,
        @NotNull Integer displayOrder,
        Boolean requiresConfirmation,
        @PositiveOrZero Integer bufferMinutes) {

    boolean requiresConfirmationOrDefault() {
        return Boolean.TRUE.equals(requiresConfirmation);
    }
}
