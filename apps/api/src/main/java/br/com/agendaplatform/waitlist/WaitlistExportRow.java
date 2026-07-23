package br.com.agendaplatform.waitlist;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record WaitlistExportRow(
        String clientName,
        String serviceName,
        LocalDate preferredStartDate,
        LocalDate preferredEndDate,
        LocalTime preferredStartTime,
        LocalTime preferredEndTime,
        String priority,
        Instant expiresAt,
        String status) {
}
