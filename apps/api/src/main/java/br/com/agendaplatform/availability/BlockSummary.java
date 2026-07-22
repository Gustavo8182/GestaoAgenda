package br.com.agendaplatform.availability;

import java.time.Instant;
import java.util.UUID;

public record BlockSummary(UUID id, Instant startAt, Instant endAt, String reason) {
}
