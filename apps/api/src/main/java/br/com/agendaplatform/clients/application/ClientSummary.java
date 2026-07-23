package br.com.agendaplatform.clients.application;

import br.com.agendaplatform.clients.domain.Client;
import java.util.UUID;

public record ClientSummary(
        UUID id,
        String name,
        String phone,
        String alternatePhone,
        String origin,
        String notes,
        boolean contactRestricted,
        String contactRestrictionReason) {

    static ClientSummary from(Client client) {
        return new ClientSummary(
                client.getId(),
                client.getName(),
                client.getPhone(),
                client.getAlternatePhone(),
                client.getOrigin(),
                client.getNotes(),
                client.isContactRestricted(),
                client.getContactRestrictionReason());
    }
}
