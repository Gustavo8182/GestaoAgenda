package br.com.agendaplatform.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Permite que outros módulos resolvam o nome de exibição de uma usuária a partir do id, sem
 * acessar as classes internas do módulo identity.
 */
public interface UserLookup {

    Optional<UserRef> find(UUID userId);
}
