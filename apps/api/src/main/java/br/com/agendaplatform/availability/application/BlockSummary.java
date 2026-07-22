package br.com.agendaplatform.availability.application;

import java.time.Instant;
import java.util.UUID;

public record BlockSummary(UUID id, Instant startAt, Instant endAt, String reason) {
}
