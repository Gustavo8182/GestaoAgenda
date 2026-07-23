package br.com.agendaplatform.relationships.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationshipContactTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID RESPONSIBLE_USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void newContactStartsAsNewContactWithLastInteractionSetToNow() {
        RelationshipContact contact = newContact();

        assertThat(contact.getStatus()).isEqualTo(RelationshipStatus.NEW_CONTACT);
        assertThat(contact.getLastInteractionAt()).isEqualTo(NOW);
        assertThat(contact.getClientId()).isNull();
        assertThat(contact.getAppointmentId()).isNull();
    }

    @Test
    void constructorRejectsBlankName() {
        assertThatThrownBy(() -> new RelationshipContact(
                        ORGANIZATION_ID, "  ", "21999999999", null, RESPONSIBLE_USER_ID, NOW))
                .isInstanceOf(InvalidRelationshipContactException.class);
    }

    @Test
    void constructorRejectsBlankPhone() {
        assertThatThrownBy(() -> new RelationshipContact(
                        ORGANIZATION_ID, "Fulana de Tal", "  ", null, RESPONSIBLE_USER_ID, NOW))
                .isInstanceOf(InvalidRelationshipContactException.class);
    }

    @Test
    void constructorTreatsBlankOriginAsNull() {
        RelationshipContact contact = new RelationshipContact(
                ORGANIZATION_ID, "Fulana de Tal", "21999999999", "   ", RESPONSIBLE_USER_ID, NOW);

        assertThat(contact.getOrigin()).isNull();
    }

    @Test
    void updateStatusChangesStatusAndTouchesLastInteraction() {
        RelationshipContact contact = newContact();
        Instant later = NOW.plusSeconds(3600);

        contact.updateStatus(RelationshipStatus.IN_SERVICE, later);

        assertThat(contact.getStatus()).isEqualTo(RelationshipStatus.IN_SERVICE);
        assertThat(contact.getLastInteractionAt()).isEqualTo(later);
    }

    @Test
    void updateNextActionSetsFieldsAndTouchesLastInteraction() {
        RelationshipContact contact = newContact();
        Instant nextActionAt = NOW.plusSeconds(86_400);
        Instant later = NOW.plusSeconds(3600);

        contact.updateNextAction("Ligar novamente", nextActionAt, later);

        assertThat(contact.getNextAction()).isEqualTo("Ligar novamente");
        assertThat(contact.getNextActionAt()).isEqualTo(nextActionAt);
        assertThat(contact.getLastInteractionAt()).isEqualTo(later);
    }

    @Test
    void updateNextActionTreatsBlankAsNull() {
        RelationshipContact contact = newContact();
        contact.updateNextAction("Ligar novamente", NOW.plusSeconds(86_400), NOW);

        contact.updateNextAction("   ", null, NOW);

        assertThat(contact.getNextAction()).isNull();
        assertThat(contact.getNextActionAt()).isNull();
    }

    @Test
    void convertSetsClientAppointmentAndScheduledStatus() {
        RelationshipContact contact = newContact();
        UUID clientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        contact.convert(clientId, appointmentId, NOW.plusSeconds(60));

        assertThat(contact.getClientId()).isEqualTo(clientId);
        assertThat(contact.getAppointmentId()).isEqualTo(appointmentId);
        assertThat(contact.getStatus()).isEqualTo(RelationshipStatus.SCHEDULED);
    }

    @Test
    void convertFailsWhenMarkedDoNotContact() {
        RelationshipContact contact = newContact();
        contact.updateStatus(RelationshipStatus.DO_NOT_CONTACT, NOW);

        assertThatThrownBy(() -> contact.convert(UUID.randomUUID(), UUID.randomUUID(), NOW))
                .isInstanceOf(RelationshipContactNotConvertibleException.class);
    }

    @Test
    void convertFailsWhenAlreadyConverted() {
        RelationshipContact contact = newContact();
        contact.convert(UUID.randomUUID(), UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> contact.convert(UUID.randomUUID(), UUID.randomUUID(), NOW))
                .isInstanceOf(RelationshipContactNotConvertibleException.class);
    }

    @Test
    void isPendingContactIsTrueWhenNextActionIsDueAndStatusIsActive() {
        RelationshipContact contact = newContact();
        contact.updateNextAction("Ligar", NOW.plusSeconds(3600), NOW);

        assertThat(contact.isPendingContact(NOW.plusSeconds(3599))).isFalse();
        assertThat(contact.isPendingContact(NOW.plusSeconds(3600))).isTrue();
        assertThat(contact.isPendingContact(NOW.plusSeconds(7200))).isTrue();
    }

    @Test
    void isPendingContactIsFalseWithoutANextActionDate() {
        RelationshipContact contact = newContact();

        assertThat(contact.isPendingContact(NOW.plusSeconds(999_999))).isFalse();
    }

    @Test
    void isPendingContactIsFalseForTerminalStatuses() {
        RelationshipContact contact = newContact();
        contact.updateNextAction("Ligar", NOW.minusSeconds(60), NOW);
        contact.updateStatus(RelationshipStatus.DID_NOT_SCHEDULE, NOW);

        assertThat(contact.isPendingContact(NOW.plusSeconds(60))).isFalse();
    }

    private RelationshipContact newContact() {
        return new RelationshipContact(
                ORGANIZATION_ID, "Fulana de Tal", "21999999999", null, RESPONSIBLE_USER_ID, NOW);
    }
}
