package br.com.agendaplatform.organizations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Permite que outros módulos gerenciem os vínculos de usuárias com uma organização (convidar,
 * listar, desativar, reativar), sem acessar as classes internas do módulo organizations.
 */
public interface MembershipRegistry {

    MemberRef addMember(UUID organizationId, UUID userId, OrganizationRole role);

    List<MemberRef> listMembers(UUID organizationId);

    Optional<MemberRef> findMember(UUID organizationId, UUID memberId);

    MemberRef disableMember(UUID organizationId, UUID memberId);

    MemberRef reactivateMember(UUID organizationId, UUID memberId);
}
