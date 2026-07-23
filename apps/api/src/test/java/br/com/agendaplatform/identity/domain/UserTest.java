package br.com.agendaplatform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void invitedUserStartsInvitedWithThePlaceholderHash() {
        User user = new User("secretaria@exemplo.test", "hash-aleatorio", "Secretária de Teste");

        assertThat(user.getEmail()).isEqualTo("secretaria@exemplo.test");
        assertThat(user.getDisplayName()).isEqualTo("Secretária de Teste");
        assertThat(user.getPasswordHash()).isEqualTo("hash-aleatorio");
        assertThat(user.getStatus()).isEqualTo(UserStatus.INVITED);
    }

    @Test
    void acceptInvitationActivatesTheUserAndSetsTheRealPassword() {
        User user = new User("secretaria@exemplo.test", "hash-aleatorio", "Secretária de Teste");

        user.acceptInvitation("hash-da-senha-real");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isEqualTo("hash-da-senha-real");
    }

    @Test
    void acceptInvitationFailsWhenUserIsNotInvited() {
        User user = new User("secretaria@exemplo.test", "hash-aleatorio", "Secretária de Teste");
        user.acceptInvitation("hash-da-senha-real");

        assertThatThrownBy(() -> user.acceptInvitation("outro-hash"))
                .isInstanceOf(InvalidUserInvitationException.class);
    }
}
