package br.com.agendaplatform.availability.domain;

public class InvalidBusinessHoursException extends RuntimeException {

    public InvalidBusinessHoursException(String message) {
        super(message);
    }
}
