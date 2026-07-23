package br.com.agendaplatform.membership.domain;

public class InvalidMembershipActionException extends RuntimeException {

    public InvalidMembershipActionException(String message) {
        super(message);
    }
}
