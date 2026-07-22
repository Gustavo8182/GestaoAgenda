package br.com.agendaplatform.clients.application;

import br.com.agendaplatform.clients.domain.Client;
import java.util.UUID;

public record ClientSummary(UUID id, String name, String phone) {

    static ClientSummary from(Client client) {
        return new ClientSummary(client.getId(), client.getName(), client.getPhone());
    }
}
