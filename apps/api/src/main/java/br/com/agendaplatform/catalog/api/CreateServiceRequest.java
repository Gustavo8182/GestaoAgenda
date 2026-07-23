package br.com.agendaplatform.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateServiceRequest(
        @NotBlank String name,
        @Positive int durationMinutes,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB.") String color,
        Integer displayOrder,
        Boolean requiresConfirmation,
        @PositiveOrZero Integer bufferMinutes) {

    public CreateServiceRequest(String name, int durationMinutes) {
        this(name, durationMinutes, null, null, null, null);
    }

    boolean requiresConfirmationOrDefault() {
        return Boolean.TRUE.equals(requiresConfirmation);
    }
}
