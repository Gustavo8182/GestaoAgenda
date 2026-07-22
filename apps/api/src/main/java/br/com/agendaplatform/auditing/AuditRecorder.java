package br.com.agendaplatform.auditing;

import java.util.Map;
import java.util.UUID;

/**
 * Contrato para registrar histórico administrativo. Toda criação, edição ou remoção de
 * entidade de negócio relevante deve chamar este contrato — não gravar diretamente na tabela.
 */
public interface AuditRecorder {

    void record(UUID organizationId, UUID actorUserId, String action, String entityType, UUID entityId);

    void record(
            UUID organizationId,
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, String> metadata);
}
