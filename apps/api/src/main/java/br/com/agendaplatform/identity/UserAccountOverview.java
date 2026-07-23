package br.com.agendaplatform.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Permite que outros módulos resolvam e-mail, nome e status de conta de uma usuária a partir do
 * id (ex.: para montar a tela de gestão de usuárias da organização), sem acessar as classes
 * internas do módulo identity.
 */
public interface UserAccountOverview {

    Optional<UserAccountSummary> find(UUID userId);
}
