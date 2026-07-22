package br.com.agendaplatform.availability.application;

import br.com.agendaplatform.auditing.AuditRecorder;
import br.com.agendaplatform.availability.domain.BusinessHours;
import br.com.agendaplatform.availability.domain.InvalidBusinessHoursException;
import br.com.agendaplatform.availability.infrastructure.BusinessHoursRepository;
import br.com.agendaplatform.organizations.CurrentOrganizationProvider;
import br.com.agendaplatform.shared.security.CurrentActorProvider;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessHoursManager {

    private final BusinessHoursRepository businessHoursRepository;
    private final CurrentOrganizationProvider currentOrganizationProvider;
    private final CurrentActorProvider currentActorProvider;
    private final AuditRecorder auditRecorder;

    BusinessHoursManager(
            BusinessHoursRepository businessHoursRepository,
            CurrentOrganizationProvider currentOrganizationProvider,
            CurrentActorProvider currentActorProvider,
            AuditRecorder auditRecorder) {
        this.businessHoursRepository = businessHoursRepository;
        this.currentOrganizationProvider = currentOrganizationProvider;
        this.currentActorProvider = currentActorProvider;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public List<BusinessHoursEntry> replace(List<BusinessHoursEntry> entries) {
        UUID organizationId = currentOrganizationProvider.current().organizationId();

        Set<java.time.DayOfWeek> seenDays = new HashSet<>();
        for (BusinessHoursEntry entry : entries) {
            if (!seenDays.add(entry.dayOfWeek())) {
                throw new InvalidBusinessHoursException(
                        "Cada dia da semana só pode ter um intervalo de funcionamento.");
            }
        }

        businessHoursRepository.deleteAllByOrganizationId(organizationId);
        for (BusinessHoursEntry entry : entries) {
            businessHoursRepository.save(
                    new BusinessHours(organizationId, entry.dayOfWeek(), entry.startTime(), entry.endTime()));
        }

        auditRecorder.record(
                organizationId,
                currentActorProvider.currentUserId(),
                "BUSINESS_HOURS_UPDATED",
                "BUSINESS_HOURS",
                organizationId);

        return list();
    }

    @Transactional(readOnly = true)
    public List<BusinessHoursEntry> list() {
        UUID organizationId = currentOrganizationProvider.current().organizationId();
        return businessHoursRepository.findAllByOrganizationId(organizationId).stream()
                .map(hours -> new BusinessHoursEntry(hours.getDayOfWeek(), hours.getStartTime(), hours.getEndTime()))
                .toList();
    }
}
