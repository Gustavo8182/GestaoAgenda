package br.com.agendaplatform.relationships.domain;

public class InvalidRelationshipContactException extends RuntimeException {

    public InvalidRelationshipContactException(String message) {
        super(message);
    }
}
