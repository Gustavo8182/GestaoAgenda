package br.com.agendaplatform.availability.infrastructure;

import br.com.agendaplatform.availability.AvailabilityCheck;
import br.com.agendaplatform.availability.domain.BusinessHours;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RepositoryAvailabilityCheck implements AvailabilityCheck {

    private final BusinessHoursRepository businessHoursRepository;
    private final BlockRepository blockRepository;

    RepositoryAvailabilityCheck(BusinessHoursRepository businessHoursRepository, BlockRepository blockRepository) {
        this.businessHoursRepository = businessHoursRepository;
        this.blockRepository = blockRepository;
    }

    @Override
    public boolean isWithinBusinessHours(UUID organizationId, String timezone, Instant startAt, Instant endAt) {
        List<BusinessHours> configured = businessHoursRepository.findAllByOrganizationId(organizationId);
        if (configured.isEmpty()) {
            return true;
        }

        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime start = startAt.atZone(zoneId);
        ZonedDateTime end = endAt.atZone(zoneId);

        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }

        DayOfWeek dayOfWeek = start.getDayOfWeek();
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();

        return configured.stream()
                .filter(hours -> hours.getDayOfWeek() == dayOfWeek)
                .anyMatch(hours -> !startTime.isBefore(hours.getStartTime()) && !endTime.isAfter(hours.getEndTime()));
    }

    @Override
    public boolean overlapsBlock(UUID organizationId, Instant startAt, Instant endAt) {
        return blockRepository.existsOverlapping(organizationId, startAt, endAt);
    }
}
