package br.com.agendaplatform.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Testa a máquina de estados do agendamento isoladamente (sem Spring/Postgres), cobrindo
 * cada transição válida e cada rejeição a partir de um status incompatível. As transições
 * já são exercitadas de ponta a ponta via HTTP em AppointmentControllerTest; este teste cobre
 * combinações de status inválido que seriam repetitivas demais para justificar uma requisição
 * HTTP completa por caso.
 */
class AppointmentTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-08-01T10:30:00Z");

    @Test
    void newAppointmentStartsAsScheduledWithoutCancellationReason() {
        Appointment appointment = newAppointment();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointment.getCancellationReason()).isNull();
    }

    @Test
    void constructorRejectsEndAtNotAfterStartAt() {
        assertThatThrownBy(() -> new Appointment(ORGANIZATION_ID, CLIENT_ID, SERVICE_ID, START, START))
                .isInstanceOf(InvalidAppointmentRangeException.class);
    }

    @Test
    void confirmMovesFromScheduledToConfirmed() {
        Appointment appointment = newAppointment();

        appointment.confirm();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = "SCHEDULED", mode = EnumSource.Mode.EXCLUDE)
    void confirmFailsFromAnyStatusOtherThanScheduled(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        assertThatThrownBy(appointment::confirm).isInstanceOf(InvalidAppointmentStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "CONFIRMED"})
    void registerArrivalMovesToArrivedFromScheduledOrConfirmed(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        appointment.registerArrival();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.ARRIVED);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "CONFIRMED"}, mode = EnumSource.Mode.EXCLUDE)
    void registerArrivalFailsFromAnyOtherStatus(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        assertThatThrownBy(appointment::registerArrival).isInstanceOf(InvalidAppointmentStateException.class);
    }

    @Test
    void startServiceMovesFromArrivedToInProgress() {
        Appointment appointment = inStatus(AppointmentStatus.ARRIVED);

        appointment.startService();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = "ARRIVED", mode = EnumSource.Mode.EXCLUDE)
    void startServiceFailsFromAnyStatusOtherThanArrived(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        assertThatThrownBy(appointment::startService).isInstanceOf(InvalidAppointmentStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"ARRIVED", "IN_PROGRESS"})
    void completeMovesToDoneFromArrivedOrInProgress(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        appointment.complete();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.DONE);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"ARRIVED", "IN_PROGRESS"}, mode = EnumSource.Mode.EXCLUDE)
    void completeFailsFromAnyOtherStatus(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        assertThatThrownBy(appointment::complete).isInstanceOf(InvalidAppointmentStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "CONFIRMED"})
    void markNoShowMovesToNoShowFromScheduledOrConfirmed(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        appointment.markNoShow();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "CONFIRMED"}, mode = EnumSource.Mode.EXCLUDE)
    void markNoShowFailsFromAnyOtherStatus(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        assertThatThrownBy(appointment::markNoShow).isInstanceOf(InvalidAppointmentStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CANCELLED", "DONE", "NO_SHOW"}, mode = EnumSource.Mode.EXCLUDE)
    void cancelMovesToCancelledAndStoresReasonFromAnyNonTerminalStatus(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        appointment.cancel("Motivo de teste");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancellationReason()).isEqualTo("Motivo de teste");
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CANCELLED", "DONE", "NO_SHOW"})
    void cancelFailsFromTerminalStatuses(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        assertThatThrownBy(() -> appointment.cancel("Motivo")).isInstanceOf(InvalidAppointmentStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "CONFIRMED"})
    void rescheduleUpdatesTimesFromScheduledOrConfirmed(AppointmentStatus status) {
        Appointment appointment = inStatus(status);
        Instant newStart = START.plusSeconds(3600);
        Instant newEnd = END.plusSeconds(3600);

        appointment.reschedule(newStart, newEnd);

        assertThat(appointment.getStartAt()).isEqualTo(newStart);
        assertThat(appointment.getEndAt()).isEqualTo(newEnd);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "CONFIRMED"}, mode = EnumSource.Mode.EXCLUDE)
    void rescheduleFailsFromAnyOtherStatus(AppointmentStatus status) {
        Appointment appointment = inStatus(status);

        assertThatThrownBy(() -> appointment.reschedule(START.plusSeconds(3600), END.plusSeconds(3600)))
                .isInstanceOf(InvalidAppointmentStateException.class);
    }

    @Test
    void rescheduleRejectsEndAtNotAfterStartAt() {
        Appointment appointment = newAppointment();

        assertThatThrownBy(() -> appointment.reschedule(START, START))
                .isInstanceOf(InvalidAppointmentRangeException.class);
    }

    private Appointment newAppointment() {
        return new Appointment(ORGANIZATION_ID, CLIENT_ID, SERVICE_ID, START, END);
    }

    private Appointment inStatus(AppointmentStatus status) {
        Appointment appointment = newAppointment();
        switch (status) {
            case SCHEDULED -> { }
            case CONFIRMED -> appointment.confirm();
            case ARRIVED -> appointment.registerArrival();
            case IN_PROGRESS -> {
                appointment.registerArrival();
                appointment.startService();
            }
            case DONE -> {
                appointment.registerArrival();
                appointment.complete();
            }
            case CANCELLED -> appointment.cancel("Motivo de configuração do teste");
            case NO_SHOW -> appointment.markNoShow();
        }
        return appointment;
    }
}
