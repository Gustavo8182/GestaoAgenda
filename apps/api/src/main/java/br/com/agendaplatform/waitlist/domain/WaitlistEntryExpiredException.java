package br.com.agendaplatform.waitlist.domain;

public class WaitlistEntryExpiredException extends RuntimeException {

    public WaitlistEntryExpiredException(String message) {
        super(message);
    }
}
