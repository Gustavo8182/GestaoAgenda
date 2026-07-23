package br.com.agendaplatform.organizations.infrastructure;

import br.com.agendaplatform.organizations.domain.OrganizationMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    @Query("select m from OrganizationMember m join fetch m.organization o "
            + "where m.userId = :userId and m.status = 'ACTIVE' and o.status = 'ACTIVE'")
    Optional<OrganizationMember> findActiveMembershipByUserId(UUID userId);

    List<OrganizationMember> findAllByOrganization_IdOrderByCreatedAtAsc(UUID organizationId);

    Optional<OrganizationMember> findByIdAndOrganization_Id(UUID id, UUID organizationId);
}
