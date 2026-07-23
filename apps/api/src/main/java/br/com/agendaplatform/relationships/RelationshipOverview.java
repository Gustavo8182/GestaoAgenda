package br.com.agendaplatform.relationships;

import java.util.List;
import java.util.UUID;

/**
 * Permite que outros módulos leiam todos os contatos de relacionamento de uma organização
 * (ex.: exportação CSV), sem acessar as classes internas do módulo relationships.
 */
public interface RelationshipOverview {

    List<RelationshipExportRow> findAll(UUID organizationId);
}
