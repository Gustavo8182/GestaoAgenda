package br.com.agendaplatform.waitlist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "preferred_start_date", nullable = false)
    private LocalDate preferredStartDate;

    @Column(name = "preferred_end_date", nullable = false)
    private LocalDate preferredEndDate;

    @Column(name = "preferred_start_time", nullable = false)
    private LocalTime preferredStartTime;

    @Column(name = "preferred_end_time", nullable = false)
    private LocalTime preferredEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private WaitlistPriority priority;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WaitlistStatus status;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(
            UUID organizationId,
            UUID clientId,
            UUID serviceId,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            WaitlistPriority priority,
            Instant expiresAt) {
        if (preferredEndDate.isBefore(preferredStartDate)) {
            throw new InvalidWaitlistEntryException("O período final deve ser depois do período inicial.");
        }
        if (!preferredEndTime.isAfter(preferredStartTime)) {
            throw new InvalidWaitlistEntryException("O horário final deve ser depois do horário inicial.");
        }

        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.clientId = clientId;
        this.serviceId = serviceId;
        this.preferredStartDate = preferredStartDate;
        this.preferredEndDate = preferredEndDate;
        this.preferredStartTime = preferredStartTime;
        this.preferredEndTime = preferredEndTime;
        this.priority = priority;
        this.expiresAt = expiresAt;
        this.status = WaitlistStatus.WAITING;
    }

    public void cancel() {
        if (status != WaitlistStatus.WAITING) {
            throw new WaitlistEntryNotWaitingException("Só é possível cancelar um registro aguardando.");
        }
        this.status = WaitlistStatus.CANCELLED;
    }

    public void convert(Instant now, UUID appointmentId) {
        if (status != WaitlistStatus.WAITING) {
            throw new WaitlistEntryNotWaitingException("Só é possível converter um registro aguardando.");
        }
        if (expiresAt.isBefore(now)) {
            throw new WaitlistEntryExpiredException("Este registro da lista de espera expirou.");
        }
        this.status = WaitlistStatus.CONVERTED;
        this.appointmentId = appointmentId;
    }

    public boolean isExpired(Instant now) {
        return status == WaitlistStatus.WAITING && expiresAt.isBefore(now);
    }

    public boolean matchesSlot(
            UUID serviceId, Instant now, LocalDate slotDate, LocalTime slotStartTime, LocalTime slotEndTime) {
        return status == WaitlistStatus.WAITING
                && !isExpired(now)
                && this.serviceId.equals(serviceId)
                && !slotDate.isBefore(preferredStartDate)
                && !slotDate.isAfter(preferredEndDate)
                && !slotStartTime.isBefore(preferredStartTime)
                && !slotEndTime.isAfter(preferredEndTime);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public LocalDate getPreferredStartDate() {
        return preferredStartDate;
    }

    public LocalDate getPreferredEndDate() {
        return preferredEndDate;
    }

    public LocalTime getPreferredStartTime() {
        return preferredStartTime;
    }

    public LocalTime getPreferredEndTime() {
        return preferredEndTime;
    }

    public WaitlistPriority getPriority() {
        return priority;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
