package br.com.agendaplatform.clients.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Test
    void normalizesPhoneAndTreatsBlankOptionalFieldsAsNull() {
        Client client = new Client(ORGANIZATION_ID, "Fulana de Tal", "(21) 99999-9999", "  ", "  ", "  ");

        assertThat(client.getPhone()).isEqualTo("(21) 99999-9999");
        assertThat(client.getPhoneNormalized()).isEqualTo("21999999999");
        assertThat(client.getAlternatePhone()).isNull();
        assertThat(client.getAlternatePhoneNormalized()).isNull();
        assertThat(client.getOrigin()).isNull();
        assertThat(client.getNotes()).isNull();
    }

    @Test
    void keepsAlternatePhoneOriginAndNotesWhenProvided() {
        Client client = new Client(
                ORGANIZATION_ID, "Fulana de Tal", "21999999999", "21988887777", "Indicação de amiga", "Prefere manhãs");

        assertThat(client.getAlternatePhone()).isEqualTo("21988887777");
        assertThat(client.getAlternatePhoneNormalized()).isEqualTo("21988887777");
        assertThat(client.getOrigin()).isEqualTo("Indicação de amiga");
        assertThat(client.getNotes()).isEqualTo("Prefere manhãs");
    }

    @Test
    void rejectsPhoneShorterThanTenDigits() {
        assertThatThrownBy(() -> new Client(ORGANIZATION_ID, "Fulana de Tal", "999999", null, null, null))
                .isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void rejectsPhoneLongerThanElevenDigits() {
        assertThatThrownBy(() -> new Client(ORGANIZATION_ID, "Fulana de Tal", "219999999999999", null, null, null))
                .isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void rejectsInvalidAlternatePhoneWithADistinctMessageFromThePrimaryPhone() {
        assertThatThrownBy(() -> new Client(ORGANIZATION_ID, "Fulana de Tal", "21999999999", "123", null, null))
                .isInstanceOf(InvalidPhoneException.class)
                .hasMessageContaining("alternativo");
    }

    @Test
    void stripsTheBrazilianCountryCodeFromThePhoneWhenNormalizing() {
        Client client = new Client(ORGANIZATION_ID, "Fulana de Tal", "+55 21 99999-9999", null, null, null);

        assertThat(client.getPhoneNormalized()).isEqualTo("21999999999");
    }

    @Test
    void newClientIsNotContactRestricted() {
        Client client = new Client(ORGANIZATION_ID, "Fulana de Tal", "21999999999", null, null, null);

        assertThat(client.isContactRestricted()).isFalse();
        assertThat(client.getContactRestrictionReason()).isNull();
    }

    @Test
    void restrictContactSetsTheFlagAndReason() {
        Client client = new Client(ORGANIZATION_ID, "Fulana de Tal", "21999999999", null, null, null);

        client.restrictContact("Pediu para não ser mais contatada.");

        assertThat(client.isContactRestricted()).isTrue();
        assertThat(client.getContactRestrictionReason()).isEqualTo("Pediu para não ser mais contatada.");
    }

    @Test
    void restrictContactTreatsBlankReasonAsNull() {
        Client client = new Client(ORGANIZATION_ID, "Fulana de Tal", "21999999999", null, null, null);

        client.restrictContact("   ");

        assertThat(client.isContactRestricted()).isTrue();
        assertThat(client.getContactRestrictionReason()).isNull();
    }

    @Test
    void liftContactRestrictionClearsTheFlagAndReason() {
        Client client = new Client(ORGANIZATION_ID, "Fulana de Tal", "21999999999", null, null, null);
        client.restrictContact("Pediu para não ser mais contatada.");

        client.liftContactRestriction();

        assertThat(client.isContactRestricted()).isFalse();
        assertThat(client.getContactRestrictionReason()).isNull();
    }
}
