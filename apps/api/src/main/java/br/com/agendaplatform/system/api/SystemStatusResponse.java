package br.com.agendaplatform.system.api;

import java.time.Instant;

public record SystemStatusResponse(String service, String status, Instant timestamp) {
}
