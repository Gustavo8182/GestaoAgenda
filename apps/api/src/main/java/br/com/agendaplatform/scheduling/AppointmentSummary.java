package br.com.agendaplatform.scheduling;

import java.time.Instant;
import java.util.UUID;

public record AppointmentSummary(
        UUID id,
        String clientName,
        String serviceName,
        Instant startAt,
        Instant endAt,
        String status,
        String cancellationReason) {
}
