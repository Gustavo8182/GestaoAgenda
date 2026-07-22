package br.com.agendaplatform.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateServiceRequest(@NotBlank String name, @Positive int durationMinutes) {
}
