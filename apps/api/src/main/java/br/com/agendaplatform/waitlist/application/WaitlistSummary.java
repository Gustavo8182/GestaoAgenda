package br.com.agendaplatform.waitlist.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record WaitlistSummary(
        UUID id,
        UUID serviceId,
        String clientName,
        String serviceName,
        LocalDate preferredStartDate,
        LocalDate preferredEndDate,
        LocalTime preferredStartTime,
        LocalTime preferredEndTime,
        String priority,
        Instant expiresAt,
        String status,
        UUID appointmentId) {
}
