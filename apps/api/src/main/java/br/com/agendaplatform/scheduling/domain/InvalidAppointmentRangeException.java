package br.com.agendaplatform.scheduling.domain;

public class InvalidAppointmentRangeException extends RuntimeException {

    public InvalidAppointmentRangeException(String message) {
        super(message);
    }
}
