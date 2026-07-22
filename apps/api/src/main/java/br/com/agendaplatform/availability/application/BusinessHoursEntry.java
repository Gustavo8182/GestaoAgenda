package br.com.agendaplatform.availability.application;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record BusinessHoursEntry(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
