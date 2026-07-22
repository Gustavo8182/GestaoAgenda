package br.com.agendaplatform.catalog.domain;

public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(String message) {
        super(message);
    }
}
