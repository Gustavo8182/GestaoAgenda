package br.com.agendaplatform.identity.infrastructure;

import br.com.agendaplatform.identity.UserLookup;
import br.com.agendaplatform.identity.UserRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RepositoryUserLookup implements UserLookup {

    private final UserRepository userRepository;

    RepositoryUserLookup(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserRef> find(UUID userId) {
        return userRepository.findById(userId).map(user -> new UserRef(user.getId(), user.getDisplayName()));
    }
}
