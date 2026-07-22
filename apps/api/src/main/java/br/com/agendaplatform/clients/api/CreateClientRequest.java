package br.com.agendaplatform.clients.api;

import jakarta.validation.constraints.NotBlank;

public record CreateClientRequest(@NotBlank String name, @NotBlank String phone) {
}
