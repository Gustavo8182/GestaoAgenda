package br.com.agendaplatform.relationships.application;

import java.time.Instant;
import java.util.UUID;

public record RelationshipSummary(
        UUID id,
        String name,
        String phone,
        String origin,
        String status,
        Instant lastInteractionAt,
        String nextAction,
        Instant nextActionAt,
        String responsibleName,
        UUID clientId,
        UUID appointmentId,
        boolean pendingContact) {
}
