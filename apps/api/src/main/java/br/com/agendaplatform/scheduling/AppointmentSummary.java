package br.com.agendaplatform.scheduling;

import java.time.Instant;
import java.util.UUID;

public record AppointmentSummary(
        UUID id,
        UUID clientId,
        String clientName,
        UUID serviceId,
        String serviceName,
        Instant startAt,
        Instant endAt,
        String status,
        String cancellationReason,
        UUID seriesId) {
}
