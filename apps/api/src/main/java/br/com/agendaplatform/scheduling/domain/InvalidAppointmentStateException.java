package br.com.agendaplatform.scheduling.domain;

public class InvalidAppointmentStateException extends RuntimeException {

    public InvalidAppointmentStateException(String message) {
        super(message);
    }
}
