package br.com.agendaplatform.relationships.api;

import br.com.agendaplatform.relationships.domain.RelationshipStatus;
import java.time.Instant;

public record UpdateRelationshipContactRequest(RelationshipStatus status, String nextAction, Instant nextActionAt) {
}
