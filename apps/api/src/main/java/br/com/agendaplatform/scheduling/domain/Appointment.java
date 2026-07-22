package br.com.agendaplatform.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {
    }

    public Appointment(UUID organizationId, UUID clientId, UUID serviceId, Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new InvalidAppointmentRangeException("O horário final deve ser depois do horário inicial.");
        }

        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.clientId = clientId;
        this.serviceId = serviceId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = AppointmentStatus.SCHEDULED;
    }

    public void reschedule(Instant newStartAt, Instant newEndAt) {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new InvalidAppointmentStateException("Não é possível remarcar um agendamento cancelado.");
        }
        if (!newEndAt.isAfter(newStartAt)) {
            throw new InvalidAppointmentRangeException("O horário final deve ser depois do horário inicial.");
        }

        this.startAt = newStartAt;
        this.endAt = newEndAt;
    }

    public void cancel(String reason) {
        if (status == AppointmentStatus.CANCELLED) {
            throw new InvalidAppointmentStateException("Este agendamento já está cancelado.");
        }

        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = reason;
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

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
