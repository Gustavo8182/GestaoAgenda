package br.com.agendaplatform.shared.security;

import java.util.UUID;

/**
 * Contrato mínimo para módulos que precisam do identificador interno da usuária autenticada
 * sem depender das classes internas do módulo identity.
 */
public interface AuthenticatedPrincipal {

    UUID userId();
}
