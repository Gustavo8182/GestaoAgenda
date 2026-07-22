package br.com.agendaplatform.identity.api;

import br.com.agendaplatform.identity.application.CurrentUser;
import br.com.agendaplatform.organizations.CurrentOrganization;

public record SessionResponse(CurrentUser user, CurrentOrganization organization) {
}
