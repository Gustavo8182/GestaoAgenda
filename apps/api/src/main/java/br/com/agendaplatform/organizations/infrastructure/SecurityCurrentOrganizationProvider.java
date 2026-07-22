package br.com.agendaplatform.organizations.infrastructure;

import br.com.agendaplatform.organizations.CurrentOrganization;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.domain.OrganizationMember;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
class SecurityCurrentOrganizationProvider implements CurrentOrganizationProvider {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final CurrentActorProvider currentActorProvider;

    private CurrentOrganization resolved;

    SecurityCurrentOrganizationProvider(
            OrganizationMemberRepository organizationMemberRepository, CurrentActorProvider currentActorProvider) {
        this.organizationMemberRepository = organizationMemberRepository;
        this.currentActorProvider = currentActorProvider;
    }

    @Override
    public CurrentOrganization current() {
        if (resolved == null) {
            resolved = resolve();
        }
        return resolved;
    }

    private CurrentOrganization resolve() {
        OrganizationMember membership = organizationMemberRepository
                .findActiveMembershipByUserId(currentActorProvider.currentUserId())
                .orElseThrow(() -> new AccessDeniedException("Usuária sem organização ativa vinculada."));

        return new CurrentOrganization(
                membership.getOrganization().getId(), membership.getOrganization().getName(), membership.getRole());
    }
}
