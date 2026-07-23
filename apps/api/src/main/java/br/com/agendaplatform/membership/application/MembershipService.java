package br.com.agendaplatform.membership.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.identity.UserAccountOverview;
import br.com.agendaplatform.identity.UserAccountSummary;
import br.com.agendaplatform.identity.UserProvisioning;
import br.com.agendaplatform.identity.UserRef;
import br.com.agendaplatform.membership.domain.InvalidMembershipActionException;
import br.com.agendaplatform.membership.domain.MemberNotFoundException;
import br.com.agendaplatform.organizations.CurrentOrganization;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.MemberRef;
import br.com.agendaplatform.organizations.MembershipRegistry;
import br.com.agendaplatform.organizations.OrganizationAccessGuard;
import br.com.agendaplatform.organizations.OrganizationRole;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import br.com.agendaplatform.shared.security.SessionRevoker;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final MembershipRegistry membershipRegistry;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final UserProvisioning userProvisioning;
    private final UserAccountOverview userAccountOverview;
    private final SessionRevoker sessionRevoker;
    private final AuditRecorder auditRecorder;

    MembershipService(
            MembershipRegistry membershipRegistry,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            OrganizationAccessGuard organizationAccessGuard,
            UserProvisioning userProvisioning,
            UserAccountOverview userAccountOverview,
            SessionRevoker sessionRevoker,
            AuditRecorder auditRecorder) {
        this.membershipRegistry = membershipRegistry;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.organizationAccessGuard = organizationAccessGuard;
        this.userProvisioning = userProvisioning;
        this.userAccountOverview = userAccountOverview;
        this.sessionRevoker = sessionRevoker;
        this.auditRecorder = auditRecorder;
    }

    /**
     * Convite é sempre para o papel SECRETARY — este painel não convida outra proprietária nem
     * concede acesso de suporte (SUPPORT é definido separadamente, fora do autoatendimento).
     */
    @Transactional
    public MemberSummary invite(String email, String displayName) {
        organizationAccessGuard.requireOwner();
        CurrentOrganization organization = currentOrganizationProvider.current();

        UserRef userRef = userProvisioning.inviteUser(email, displayName, organization.organizationName());
        MemberRef member = membershipRegistry.addMember(
                organization.organizationId(), userRef.id(), OrganizationRole.SECRETARY);

        auditRecorder.record(
                organization.organizationId(),
                currentActorProvider.currentUserId(),
                "MEMBER_INVITED",
                "ORGANIZATION_MEMBER",
                member.id());

        return toSummary(member);
    }

    @Transactional(readOnly = true)
    public List<MemberSummary> list() {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        return membershipRegistry.listMembers(organizationId).stream().map(this::toSummary).toList();
    }

    @Transactional
    public MemberSummary disable(UUID memberId) {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        MemberRef member = findOrThrow(organizationId, memberId);

        if (member.userId().equals(currentActorProvider.currentUserId())) {
            throw new InvalidMembershipActionException("Você não pode desativar a si mesma.");
        }
        if (member.role().equals(OrganizationRole.OWNER.name())) {
            throw new InvalidMembershipActionException("Não é possível desativar a proprietária da organização.");
        }

        MemberRef disabled = membershipRegistry.disableMember(organizationId, memberId);
        sessionRevoker.revokeSessionsFor(requireAccount(member.userId()).email());

        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), "MEMBER_DISABLED", "ORGANIZATION_MEMBER", memberId);

        return toSummary(disabled);
    }

    @Transactional
    public MemberSummary reactivate(UUID memberId) {
        organizationAccessGuard.requireOwner();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        findOrThrow(organizationId, memberId);

        MemberRef reactivated = membershipRegistry.reactivateMember(organizationId, memberId);

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "MEMBER_REACTIVATED",
                "ORGANIZATION_MEMBER",
                memberId);

        return toSummary(reactivated);
    }

    private MemberRef findOrThrow(UUID organizationId, UUID memberId) {
        return membershipRegistry
                .findMember(organizationId, memberId)
                .orElseThrow(() -> new MemberNotFoundException("Membro não encontrado."));
    }

    private UserAccountSummary requireAccount(UUID userId) {
        return userAccountOverview
                .find(userId)
                .orElseThrow(() -> new IllegalStateException("Usuária do vínculo não encontrada: " + userId));
    }

    private MemberSummary toSummary(MemberRef member) {
        UserAccountSummary account = requireAccount(member.userId());
        return new MemberSummary(
                member.id(), account.email(), account.displayName(), member.role(), resolveDisplayStatus(member, account));
    }

    /**
     * O status exibido combina o vínculo com a organização e o status da conta: uma vez
     * desativado o vínculo, não importa se a conta em si continua ativa (ela só perde acesso a
     * esta organização); enquanto o vínculo estiver ativo, "convite pendente" é mais claro para
     * a proprietária do que o status técnico da conta.
     */
    private static String resolveDisplayStatus(MemberRef member, UserAccountSummary account) {
        if ("DISABLED".equals(member.status())) {
            return "DISABLED";
        }
        if ("INVITED".equals(account.status())) {
            return "INVITED";
        }
        return "ACTIVE";
    }
}
