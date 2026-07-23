package br.com.agendaplatform.reporting.application;

import br.com.agendaplatform.availability.BlockLookup;
import br.com.agendaplatform.availability.BlockSummary;
import br.com.agendaplatform.organizations.CurrentOrganization;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.organizations.OrganizationAccessGuard;
import br.com.agendaplatform.scheduling.AppointmentOverview;
import br.com.agendaplatform.scheduling.AppointmentSummary;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final AppointmentOverview appointmentOverview;
    private final BlockLookup blockLookup;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final Clock clock;
    private final OrganizationAccessGuard organizationAccessGuard;

    DashboardService(
            AppointmentOverview appointmentOverview,
            BlockLookup blockLookup,
            CurrentOrganizationProvider currentOrganizationProvider,
            Clock clock,
            OrganizationAccessGuard organizationAccessGuard) {
        this.appointmentOverview = appointmentOverview;
        this.blockLookup = blockLookup;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.clock = clock;
        this.organizationAccessGuard = organizationAccessGuard;
    }

    @Transactional(readOnly = true)
    public DashboardSummary today() {
        organizationAccessGuard.requireOperator();
        CurrentOrganization organization = currentOrganizationProvider.current();
        ZoneId zoneId = ZoneId.of(organization.timezone());
        Instant now = clock.instant();
        LocalDate today = now.atZone(zoneId).toLocalDate();

        Instant startOfDay = today.atStartOfDay(zoneId).toInstant();
        Instant startOfNextDay = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        LocalDate startOfWeekDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant startOfWeek = startOfWeekDate.atStartOfDay(zoneId).toInstant();
        Instant startOfNextWeek = startOfWeekDate.plusDays(7).atStartOfDay(zoneId).toInstant();

        List<AppointmentSummary> todayAppointments =
                appointmentOverview.findByStartAtBetween(organization.organizationId(), startOfDay, startOfNextDay);
        AppointmentSummary nextAppointment = appointmentOverview
                .findNextUpcoming(organization.organizationId(), now)
                .orElse(null);
        List<BlockSummary> todayBlocks =
                blockLookup.findOverlapping(organization.organizationId(), startOfDay, startOfNextDay);

        List<AppointmentSummary> weekAppointments =
                appointmentOverview.findByStartAtBetween(organization.organizationId(), startOfWeek, startOfNextWeek);
        long completedCount =
                weekAppointments.stream().filter(a -> "DONE".equals(a.status())).count();
        long cancelledCount =
                weekAppointments.stream().filter(a -> "CANCELLED".equals(a.status())).count();
        long noShowCount =
                weekAppointments.stream().filter(a -> "NO_SHOW".equals(a.status())).count();
        long scheduledCount = weekAppointments.size() - completedCount - cancelledCount - noShowCount;
        WeekSummary week =
                new WeekSummary((int) scheduledCount, (int) completedCount, (int) cancelledCount, (int) noShowCount);

        return new DashboardSummary(todayAppointments, nextAppointment, todayBlocks, week);
    }
}
