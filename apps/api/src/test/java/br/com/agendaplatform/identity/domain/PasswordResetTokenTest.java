package br.com.agendaplatform.identity.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetTokenTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void markUsedSucceedsBeforeExpiry() {
        PasswordResetToken token = new PasswordResetToken(USER_ID, "hash", NOW.plusSeconds(3600));

        token.markUsed(NOW);
    }

    @Test
    void markUsedFailsWhenAlreadyUsed() {
        PasswordResetToken token = new PasswordResetToken(USER_ID, "hash", NOW.plusSeconds(3600));
        token.markUsed(NOW);

        assertThatThrownBy(() -> token.markUsed(NOW)).isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void markUsedFailsWhenExpired() {
        PasswordResetToken token = new PasswordResetToken(USER_ID, "hash", NOW.minusSeconds(1));

        assertThatThrownBy(() -> token.markUsed(NOW)).isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void invalidateBlocksAFuturePendingUse() {
        PasswordResetToken token = new PasswordResetToken(USER_ID, "hash", NOW.plusSeconds(3600));

        token.invalidate(NOW);

        assertThatThrownBy(() -> token.markUsed(NOW.plusSeconds(1)))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void invalidateDoesNotThrowEvenWhenAlreadyExpired() {
        PasswordResetToken token = new PasswordResetToken(USER_ID, "hash", NOW.minusSeconds(1));

        token.invalidate(NOW);
    }

    @Test
    void invalidateDoesNotOverwriteAGenuineEarlierUse() {
        PasswordResetToken token = new PasswordResetToken(USER_ID, "hash", NOW.plusSeconds(3600));
        token.markUsed(NOW);

        token.invalidate(NOW.plusSeconds(10));

        assertThatThrownBy(() -> token.markUsed(NOW.plusSeconds(20)))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }
}
