package br.com.agendaplatform.scheduling.domain;

public class BlockedTimeException extends RuntimeException {

    public BlockedTimeException(String message) {
        super(message);
    }
}
