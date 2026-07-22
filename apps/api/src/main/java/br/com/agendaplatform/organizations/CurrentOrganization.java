package br.com.agendaplatform.organizations;

import java.util.UUID;

public record CurrentOrganization(UUID organizationId, String organizationName, OrganizationRole role) {
}
