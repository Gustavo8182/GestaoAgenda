package br.com.agendaplatform.clients.application;

public record CreateClientResult(ClientSummary client, boolean possibleDuplicate) {
}
