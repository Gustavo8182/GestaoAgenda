package br.com.agendaplatform.shared.security;

import java.util.UUID;

/**
 * Resolve o identificador da usuária autenticada na requisição atual, para uso em auditoria
 * e em regras que precisam saber quem está agindo, sem acoplar módulos ao identity.
 */
public interface CurrentActorProvider {

    UUID currentUserId();
}
