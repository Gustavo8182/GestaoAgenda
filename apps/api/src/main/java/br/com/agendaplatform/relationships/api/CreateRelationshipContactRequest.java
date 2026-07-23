package br.com.agendaplatform.relationships.api;

import jakarta.validation.constraints.NotBlank;

public record CreateRelationshipContactRequest(@NotBlank String name, @NotBlank String phone, String origin) {
}
