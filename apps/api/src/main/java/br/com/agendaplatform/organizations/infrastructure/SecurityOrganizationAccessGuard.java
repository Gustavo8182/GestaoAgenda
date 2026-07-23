package br.com.agendaplatform.organizations.infrastructure;

import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.OrganizationAccessGuard;
import br.com.agendaplatform.organizations.OrganizationRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class SecurityOrganizationAccessGuard implements OrganizationAccessGuard {

    private final CurrentOrganizationProvider currentOrganizationProvider;

    SecurityOrganizationAccessGuard(CurrentOrganizationProvider currentOrganizationProvider) {
        this.currentOrganizationProvider = currentOrganizationProvider;
    }

    @Override
    public void requireOwner() {
        if (currentOrganizationProvider.current().role() != OrganizationRole.OWNER) {
            throw new AccessDeniedException("Ação restrita à proprietária da organização.");
        }
    }
}
