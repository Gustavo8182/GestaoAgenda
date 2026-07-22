package br.com.agendaplatform.scheduling.api;

import jakarta.validation.constraints.NotBlank;

public record CancelAppointmentRequest(@NotBlank String reason) {
}
