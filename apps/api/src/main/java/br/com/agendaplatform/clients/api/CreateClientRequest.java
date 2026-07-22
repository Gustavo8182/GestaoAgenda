package br.com.agendaplatform.clients.api;

import jakarta.validation.constraints.NotBlank;

public record CreateClientRequest(
        @NotBlank String name, @NotBlank String phone, String alternatePhone, String origin, String notes) {

    public CreateClientRequest(String name, String phone) {
        this(name, phone, null, null, null);
    }
}
