package br.com.agendaplatform.organizations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.agendaplatform.organizations.OrganizationRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationMemberTest {

    private final Organization organization = mock(Organization.class);

    @Test
    void newMemberStartsActiveWithTheGivenRole() {
        UUID userId = UUID.randomUUID();
        OrganizationMember member = new OrganizationMember(organization, userId, OrganizationRole.SECRETARY);

        assertThat(member.getUserId()).isEqualTo(userId);
        assertThat(member.getRole()).isEqualTo(OrganizationRole.SECRETARY);
        assertThat(member.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void disableMarksTheMembershipAsDisabled() {
        OrganizationMember member = new OrganizationMember(organization, UUID.randomUUID(), OrganizationRole.SECRETARY);

        member.disable();

        assertThat(member.getStatus()).isEqualTo(MembershipStatus.DISABLED);
    }

    @Test
    void activateMarksADisabledMembershipAsActiveAgain() {
        OrganizationMember member = new OrganizationMember(organization, UUID.randomUUID(), OrganizationRole.SECRETARY);
        member.disable();

        member.activate();

        assertThat(member.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }
}
