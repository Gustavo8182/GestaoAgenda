package br.com.agendaplatform.organizations.infrastructure;

import br.com.agendaplatform.organizations.CurrentOrganization;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.domain.OrganizationMember;
import br.com.agendaplatform.shared.security.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
class SecurityCurrentOrganizationProvider implements CurrentOrganizationProvider {

    private final OrganizationMemberRepository organizationMemberRepository;

    private CurrentOrganization resolved;

    SecurityCurrentOrganizationProvider(OrganizationMemberRepository organizationMemberRepository) {
        this.organizationMemberRepository = organizationMemberRepository;
    }

    @Override
    public CurrentOrganization current() {
        if (resolved == null) {
            resolved = resolve();
        }
        return resolved;
    }

    private CurrentOrganization resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AccessDeniedException("Sessão sem identidade resolvida.");
        }

        OrganizationMember membership = organizationMemberRepository
                .findActiveMembershipByUserId(principal.userId())
                .orElseThrow(() -> new AccessDeniedException("Usuária sem organização ativa vinculada."));

        return new CurrentOrganization(
                membership.getOrganization().getId(), membership.getOrganization().getName(), membership.getRole());
    }
}
