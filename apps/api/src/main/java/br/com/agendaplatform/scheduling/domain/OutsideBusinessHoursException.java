package br.com.agendaplatform.scheduling.domain;

public class OutsideBusinessHoursException extends RuntimeException {

    public OutsideBusinessHoursException(String message) {
        super(message);
    }
}
