package br.com.agendaplatform.availability.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.availability.BlockSummary;
import br.com.agendaplatform.availability.domain.Block;
import br.com.agendaplatform.availability.domain.BlockNotFoundException;
import br.com.agendaplatform.availability.infrastructure.BlockRepository;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.OrganizationAccessGuard;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockManager {

    private final BlockRepository blockRepository;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;
    private final OrganizationAccessGuard organizationAccessGuard;

    BlockManager(
            BlockRepository blockRepository,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder,
            OrganizationAccessGuard organizationAccessGuard) {
        this.blockRepository = blockRepository;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
        this.organizationAccessGuard = organizationAccessGuard;
    }

    @Transactional
    public BlockSummary create(Instant startAt, Instant endAt, String reason) {
        organizationAccessGuard.requireOperator();
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        Block block = new Block(organizationId, startAt, endAt, reason);
        blockRepository.save(block);

        auditRecorder.record(
                organizationId, currentActorProvider.currentUserId(), "BLOCK_CREATED", "BLOCK", block.getId());

        return toSummary(block);
    }

    @Transactional(readOnly = true)
    public List<BlockSummary> list() {
        organizationAccessGuard.requireOperator();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return blockRepository.findAllByOrganizationIdOrderByStartAtAsc(organizationId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void remove(UUID blockId) {
        organizationAccessGuard.requireOperator();
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        Block block = blockRepository
                .findByIdAndOrganizationId(blockId, organizationId)
                .orElseThrow(() -> new BlockNotFoundException("Bloqueio não encontrado."));

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "BLOCK_REMOVED",
                "BLOCK",
                block.getId(),
                Map.of(
                        "startAt", block.getStartAt().toString(),
                        "endAt", block.getEndAt().toString(),
                        "reason", block.getReason()));

        blockRepository.delete(block);
    }

    private BlockSummary toSummary(Block block) {
        return new BlockSummary(block.getId(), block.getStartAt(), block.getEndAt(), block.getReason());
    }
}
