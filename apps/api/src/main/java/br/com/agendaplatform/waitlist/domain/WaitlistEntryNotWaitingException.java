package br.com.agendaplatform.waitlist.domain;

public class WaitlistEntryNotWaitingException extends RuntimeException {

    public WaitlistEntryNotWaitingException(String message) {
        super(message);
    }
}
