package br.com.agendaplatform.relationships.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReassignRelationshipContactRequest(@NotNull UUID responsibleUserId) {
}
