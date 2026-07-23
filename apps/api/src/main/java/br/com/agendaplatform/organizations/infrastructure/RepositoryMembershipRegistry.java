package br.com.agendaplatform.organizations.infrastructure;

import br.com.agendaplatform.organizations.MemberRef;
import br.com.agendaplatform.organizations.MembershipRegistry;
import br.com.agendaplatform.organizations.OrganizationRole;
import br.com.agendaplatform.organizations.domain.Organization;
import br.com.agendaplatform.organizations.domain.OrganizationMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RepositoryMembershipRegistry implements MembershipRegistry {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationRepository organizationRepository;

    RepositoryMembershipRegistry(
            OrganizationMemberRepository organizationMemberRepository, OrganizationRepository organizationRepository) {
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public MemberRef addMember(UUID organizationId, UUID userId, OrganizationRole role) {
        Organization organization = organizationRepository.getReferenceById(organizationId);
        OrganizationMember member = new OrganizationMember(organization, userId, role);
        organizationMemberRepository.save(member);
        return toRef(member);
    }

    @Override
    public List<MemberRef> listMembers(UUID organizationId) {
        return organizationMemberRepository.findAllByOrganization_IdOrderByCreatedAtAsc(organizationId).stream()
                .map(RepositoryMembershipRegistry::toRef)
                .toList();
    }

    @Override
    public Optional<MemberRef> findMember(UUID organizationId, UUID memberId) {
        return organizationMemberRepository
                .findByIdAndOrganization_Id(memberId, organizationId)
                .map(RepositoryMembershipRegistry::toRef);
    }

    @Override
    public MemberRef disableMember(UUID organizationId, UUID memberId) {
        OrganizationMember member = findOrThrow(organizationId, memberId);
        member.disable();
        return toRef(member);
    }

    @Override
    public MemberRef reactivateMember(UUID organizationId, UUID memberId) {
        OrganizationMember member = findOrThrow(organizationId, memberId);
        member.activate();
        return toRef(member);
    }

    private OrganizationMember findOrThrow(UUID organizationId, UUID memberId) {
        return organizationMemberRepository
                .findByIdAndOrganization_Id(memberId, organizationId)
                .orElseThrow(() -> new IllegalStateException("Membro não encontrado: " + memberId));
    }

    private static MemberRef toRef(OrganizationMember member) {
        return new MemberRef(member.getId(), member.getUserId(), member.getRole().name(), member.getStatus().name());
    }
}
