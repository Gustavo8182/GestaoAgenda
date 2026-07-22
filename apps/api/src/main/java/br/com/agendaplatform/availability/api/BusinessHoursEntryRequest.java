package br.com.agendaplatform.availability.api;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record BusinessHoursEntryRequest(
        @NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {
}
