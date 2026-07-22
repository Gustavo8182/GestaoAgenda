package br.com.agendaplatform.identity.application;

import br.com.agendaplatform.identity.domain.User;
import java.util.UUID;

public record CurrentUser(UUID id, String email, String displayName) {

    static CurrentUser from(User user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
