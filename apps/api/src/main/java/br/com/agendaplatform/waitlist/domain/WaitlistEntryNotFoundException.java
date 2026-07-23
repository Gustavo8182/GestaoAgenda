package br.com.agendaplatform.waitlist.domain;

public class WaitlistEntryNotFoundException extends RuntimeException {

    public WaitlistEntryNotFoundException(String message) {
        super(message);
    }
}
