package br.com.agendaplatform.identity.infrastructure;

import br.com.agendaplatform.identity.domain.UserInvitationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvitationTokenRepository extends JpaRepository<UserInvitationToken, UUID> {

    Optional<UserInvitationToken> findByTokenHash(String tokenHash);
}
