package br.com.agendaplatform.scheduling.domain;

public enum RecurrenceFrequency {
    WEEKLY(7),
    BIWEEKLY(14);

    private final int intervalDays;

    RecurrenceFrequency(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public int intervalDays() {
        return intervalDays;
    }
}
