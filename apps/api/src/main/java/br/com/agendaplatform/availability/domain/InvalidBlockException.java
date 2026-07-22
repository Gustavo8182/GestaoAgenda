package br.com.agendaplatform.availability.domain;

public class InvalidBlockException extends RuntimeException {

    public InvalidBlockException(String message) {
        super(message);
    }
}
