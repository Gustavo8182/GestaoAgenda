package br.com.agendaplatform.reporting.application;

import br.com.agendaplatform.availability.BlockSummary;
import br.com.agendaplatform.scheduling.AppointmentSummary;
import java.util.List;

public record DashboardSummary(
        List<AppointmentSummary> todayAppointments,
        AppointmentSummary nextAppointment,
        List<BlockSummary> todayBlocks,
        WeekSummary week) {
}
