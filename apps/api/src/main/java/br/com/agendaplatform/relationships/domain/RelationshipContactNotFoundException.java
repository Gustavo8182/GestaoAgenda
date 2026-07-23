package br.com.agendaplatform.relationships.domain;

public class RelationshipContactNotFoundException extends RuntimeException {

    public RelationshipContactNotFoundException(String message) {
        super(message);
    }
}
