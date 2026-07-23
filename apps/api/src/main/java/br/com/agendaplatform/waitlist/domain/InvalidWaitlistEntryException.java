package br.com.agendaplatform.waitlist.domain;

public class InvalidWaitlistEntryException extends RuntimeException {

    public InvalidWaitlistEntryException(String message) {
        super(message);
    }
}
