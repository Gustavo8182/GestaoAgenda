package br.com.agendaplatform.waitlist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaitlistEntryTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalTime START_TIME = LocalTime.of(9, 0);
    private static final LocalTime END_TIME = LocalTime.of(12, 0);
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void newEntryStartsAsWaiting() {
        WaitlistEntry entry = newEntry();

        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(entry.getAppointmentId()).isNull();
    }

    @Test
    void constructorRejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> new WaitlistEntry(
                        ORGANIZATION_ID,
                        CLIENT_ID,
                        SERVICE_ID,
                        START_DATE,
                        START_DATE.minusDays(1),
                        START_TIME,
                        END_TIME,
                        WaitlistPriority.NORMAL,
                        EXPIRES_AT))
                .isInstanceOf(InvalidWaitlistEntryException.class);
    }

    @Test
    void constructorRejectsEndTimeNotAfterStartTime() {
        assertThatThrownBy(() -> new WaitlistEntry(
                        ORGANIZATION_ID,
                        CLIENT_ID,
                        SERVICE_ID,
                        START_DATE,
                        END_DATE,
                        START_TIME,
                        START_TIME,
                        WaitlistPriority.NORMAL,
                        EXPIRES_AT))
                .isInstanceOf(InvalidWaitlistEntryException.class);
    }

    @Test
    void cancelMovesToCancelled() {
        WaitlistEntry entry = newEntry();

        entry.cancel();

        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
    }

    @Test
    void cancelFailsWhenAlreadyCancelled() {
        WaitlistEntry entry = newEntry();
        entry.cancel();

        assertThatThrownBy(entry::cancel).isInstanceOf(WaitlistEntryNotWaitingException.class);
    }

    @Test
    void convertMovesToConvertedAndStoresAppointmentId() {
        WaitlistEntry entry = newEntry();
        UUID appointmentId = UUID.randomUUID();

        entry.convert(NOW, appointmentId);

        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.CONVERTED);
        assertThat(entry.getAppointmentId()).isEqualTo(appointmentId);
    }

    @Test
    void convertFailsWhenNotWaiting() {
        WaitlistEntry entry = newEntry();
        entry.cancel();

        assertThatThrownBy(() -> entry.convert(NOW, UUID.randomUUID()))
                .isInstanceOf(WaitlistEntryNotWaitingException.class);
    }

    @Test
    void convertFailsWhenExpired() {
        WaitlistEntry entry = newEntry();

        assertThatThrownBy(() -> entry.convert(EXPIRES_AT.plusSeconds(1), UUID.randomUUID()))
                .isInstanceOf(WaitlistEntryExpiredException.class);
    }

    @Test
    void isExpiredIsFalseBeforeExpiryAndTrueAfter() {
        WaitlistEntry entry = newEntry();

        assertThat(entry.isExpired(EXPIRES_AT.minusSeconds(1))).isFalse();
        assertThat(entry.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
    }

    @Test
    void isExpiredIsFalseWhenNotWaitingRegardlessOfDate() {
        WaitlistEntry entry = newEntry();
        entry.cancel();

        assertThat(entry.isExpired(EXPIRES_AT.plusSeconds(1))).isFalse();
    }

    @Test
    void matchesSlotWhenServiceDateAndTimeAreWithinPreferredWindow() {
        WaitlistEntry entry = newEntry();

        boolean matches = entry.matchesSlot(SERVICE_ID, NOW, LocalDate.of(2026, 8, 5), LocalTime.of(9, 30), LocalTime.of(10, 0));

        assertThat(matches).isTrue();
    }

    @Test
    void doesNotMatchSlotForADifferentService() {
        WaitlistEntry entry = newEntry();

        boolean matches =
                entry.matchesSlot(UUID.randomUUID(), NOW, LocalDate.of(2026, 8, 5), LocalTime.of(9, 30), LocalTime.of(10, 0));

        assertThat(matches).isFalse();
    }

    @Test
    void doesNotMatchSlotOutsidePreferredDateRange() {
        WaitlistEntry entry = newEntry();

        boolean matches =
                entry.matchesSlot(SERVICE_ID, NOW, LocalDate.of(2026, 8, 20), LocalTime.of(9, 30), LocalTime.of(10, 0));

        assertThat(matches).isFalse();
    }

    @Test
    void doesNotMatchSlotOutsidePreferredTimeWindow() {
        WaitlistEntry entry = newEntry();

        boolean matches =
                entry.matchesSlot(SERVICE_ID, NOW, LocalDate.of(2026, 8, 5), LocalTime.of(13, 0), LocalTime.of(13, 30));

        assertThat(matches).isFalse();
    }

    @Test
    void doesNotMatchSlotWhenNotWaiting() {
        WaitlistEntry entry = newEntry();
        entry.cancel();

        boolean matches = entry.matchesSlot(SERVICE_ID, NOW, LocalDate.of(2026, 8, 5), LocalTime.of(9, 30), LocalTime.of(10, 0));

        assertThat(matches).isFalse();
    }

    @Test
    void doesNotMatchSlotWhenExpired() {
        WaitlistEntry entry = newEntry();

        boolean matches = entry.matchesSlot(
                SERVICE_ID, EXPIRES_AT.plusSeconds(1), LocalDate.of(2026, 8, 5), LocalTime.of(9, 30), LocalTime.of(10, 0));

        assertThat(matches).isFalse();
    }

    private WaitlistEntry newEntry() {
        return new WaitlistEntry(
                ORGANIZATION_ID,
                CLIENT_ID,
                SERVICE_ID,
                START_DATE,
                END_DATE,
                START_TIME,
                END_TIME,
                WaitlistPriority.NORMAL,
                EXPIRES_AT);
    }
}
