package br.com.agendaplatform.organizations.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Testa isoladamente (sem Spring/Postgres) o tratamento de defesa em profundidade para o caso de
 * uma usuária acabar com mais de um vínculo ativo — a constraint única em banco (V018) já deveria
 * impedir esse estado, mas o provider nunca deve deixar a exceção do Spring Data vazar como 500.
 */
class SecurityCurrentOrganizationProviderTest {

    @Test
    void currentThrowsAccessDeniedInsteadOfLeakingWhenUserHasMoreThanOneActiveMembership() {
        OrganizationMemberRepository repository = mock(OrganizationMemberRepository.class);
        CurrentActorProvider currentActorProvider = mock(CurrentActorProvider.class);
        UUID userId = UUID.randomUUID();
        when(currentActorProvider.currentUserId()).thenReturn(userId);
        when(repository.findActiveMembershipByUserId(userId))
                .thenThrow(new IncorrectResultSizeDataAccessException(1));

        SecurityCurrentOrganizationProvider provider =
                new SecurityCurrentOrganizationProvider(repository, currentActorProvider);

        assertThatThrownBy(provider::current).isInstanceOf(AccessDeniedException.class);
    }
}
