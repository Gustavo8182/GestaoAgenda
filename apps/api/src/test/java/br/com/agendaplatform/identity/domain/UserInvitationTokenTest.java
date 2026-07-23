package br.com.agendaplatform.identity.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserInvitationTokenTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void markUsedSucceedsBeforeExpiry() {
        UserInvitationToken token = new UserInvitationToken(USER_ID, "hash", NOW.plusSeconds(3600));

        token.markUsed(NOW);
    }

    @Test
    void markUsedFailsWhenAlreadyUsed() {
        UserInvitationToken token = new UserInvitationToken(USER_ID, "hash", NOW.plusSeconds(3600));
        token.markUsed(NOW);

        assertThatThrownBy(() -> token.markUsed(NOW)).isInstanceOf(InvalidUserInvitationException.class);
    }

    @Test
    void markUsedFailsWhenExpired() {
        UserInvitationToken token = new UserInvitationToken(USER_ID, "hash", NOW.minusSeconds(1));

        assertThatThrownBy(() -> token.markUsed(NOW)).isInstanceOf(InvalidUserInvitationException.class);
    }
}
