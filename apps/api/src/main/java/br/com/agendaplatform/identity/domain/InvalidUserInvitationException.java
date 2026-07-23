package br.com.agendaplatform.identity.domain;

public class InvalidUserInvitationException extends RuntimeException {

    public InvalidUserInvitationException(String message) {
        super(message);
    }
}
