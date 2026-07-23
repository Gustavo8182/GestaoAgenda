package br.com.agendaplatform.identity.infrastructure;

import br.com.agendaplatform.identity.UserAccountOverview;
import br.com.agendaplatform.identity.UserAccountSummary;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RepositoryUserAccountOverview implements UserAccountOverview {

    private final UserRepository userRepository;

    RepositoryUserAccountOverview(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserAccountSummary> find(UUID userId) {
        return userRepository
                .findById(userId)
                .map(user -> new UserAccountSummary(
                        user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus().name()));
    }
}
