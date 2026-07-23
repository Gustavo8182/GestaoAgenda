package br.com.agendaplatform.catalog;

import java.util.UUID;

public record ServiceRef(UUID id, String name, int durationMinutes, int bufferMinutes) {
}
