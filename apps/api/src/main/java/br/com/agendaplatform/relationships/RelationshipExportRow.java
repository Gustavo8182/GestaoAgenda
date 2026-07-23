package br.com.agendaplatform.relationships;

import java.time.Instant;

public record RelationshipExportRow(
        String name,
        String phone,
        String origin,
        String status,
        Instant lastInteractionAt,
        String nextAction,
        Instant nextActionAt,
        String responsibleName) {
}
