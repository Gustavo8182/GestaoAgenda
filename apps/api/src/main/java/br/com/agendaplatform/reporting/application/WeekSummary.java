package br.com.agendaplatform.reporting.application;

public record WeekSummary(int scheduledCount, int completedCount, int cancelledCount, int noShowCount) {
}
