package br.com.agendaplatform.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
