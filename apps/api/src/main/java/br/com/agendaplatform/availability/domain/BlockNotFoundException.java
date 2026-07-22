package br.com.agendaplatform.availability.domain;

public class BlockNotFoundException extends RuntimeException {

    public BlockNotFoundException(String message) {
        super(message);
    }
}
