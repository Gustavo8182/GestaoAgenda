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

    @Column(name = "series_id")
    private UUID seriesId;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {
    }

    public Appointment(UUID organizationId, UUID clientId, UUID serviceId, Instant startAt, Instant endAt) {
        this(organizationId, clientId, serviceId, startAt, endAt, 0);
    }

    /**
     * @param bufferMinutes cópia do intervalo configurado no serviço no momento da criação —
     *     ver {@code AppointmentScheduler} e a migração V017 para o porquê de ser gravado na
     *     própria linha do agendamento, em vez de consultado do catálogo a cada checagem.
     */
    public Appointment(
            UUID organizationId, UUID clientId, UUID serviceId, Instant startAt, Instant endAt, int bufferMinutes) {
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
        this.bufferMinutes = bufferMinutes;
    }

    public Appointment(
            UUID organizationId, UUID clientId, UUID serviceId, Instant startAt, Instant endAt, UUID seriesId) {
        this(organizationId, clientId, serviceId, startAt, endAt, 0, seriesId);
    }

    public Appointment(
            UUID organizationId,
            UUID clientId,
            UUID serviceId,
            Instant startAt,
            Instant endAt,
            int bufferMinutes,
            UUID seriesId) {
        this(organizationId, clientId, serviceId, startAt, endAt, bufferMinutes);
        this.seriesId = seriesId;
    }

    public void reschedule(Instant newStartAt, Instant newEndAt) {
        if (status != AppointmentStatus.SCHEDULED && status != AppointmentStatus.CONFIRMED) {
            throw new InvalidAppointmentStateException("Só é possível remarcar um agendamento agendado ou confirmado.");
        }
        if (!newEndAt.isAfter(newStartAt)) {
            throw new InvalidAppointmentRangeException("O horário final deve ser depois do horário inicial.");
        }

        this.startAt = newStartAt;
        this.endAt = newEndAt;
    }

    public void edit(UUID newClientId, UUID newServiceId, Instant newEndAt, int newBufferMinutes) {
        if (status != AppointmentStatus.SCHEDULED && status != AppointmentStatus.CONFIRMED) {
            throw new InvalidAppointmentStateException("Só é possível editar um agendamento agendado ou confirmado.");
        }
        if (!newEndAt.isAfter(startAt)) {
            throw new InvalidAppointmentRangeException("O horário final deve ser depois do horário inicial.");
        }

        this.clientId = newClientId;
        this.serviceId = newServiceId;
        this.endAt = newEndAt;
        this.bufferMinutes = newBufferMinutes;
    }

    public void cancel(String reason) {
        if (status == AppointmentStatus.CANCELLED
                || status == AppointmentStatus.DONE
                || status == AppointmentStatus.NO_SHOW) {
            throw new InvalidAppointmentStateException(
                    "Não é possível cancelar um agendamento cancelado, realizado ou com falta registrada.");
        }

        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    public void confirm() {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new InvalidAppointmentStateException("Só é possível confirmar um agendamento agendado.");
        }

        this.status = AppointmentStatus.CONFIRMED;
    }

    public void registerArrival() {
        if (status != AppointmentStatus.SCHEDULED && status != AppointmentStatus.CONFIRMED) {
            throw new InvalidAppointmentStateException(
                    "Só é possível registrar chegada de um agendamento agendado ou confirmado.");
        }

        this.status = AppointmentStatus.ARRIVED;
    }

    public void startService() {
        if (status != AppointmentStatus.ARRIVED) {
            throw new InvalidAppointmentStateException("Só é possível iniciar o atendimento após registrar a chegada.");
        }

        this.status = AppointmentStatus.IN_PROGRESS;
    }

    public void complete() {
        if (status != AppointmentStatus.ARRIVED && status != AppointmentStatus.IN_PROGRESS) {
            throw new InvalidAppointmentStateException(
                    "Só é possível concluir um agendamento com chegada registrada ou em atendimento.");
        }

        this.status = AppointmentStatus.DONE;
    }

    public void markNoShow() {
        if (status != AppointmentStatus.SCHEDULED && status != AppointmentStatus.CONFIRMED) {
            throw new InvalidAppointmentStateException(
                    "Só é possível registrar falta de um agendamento agendado ou confirmado.");
        }

        this.status = AppointmentStatus.NO_SHOW;
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

    public UUID getSeriesId() {
        return seriesId;
    }

    public int getBufferMinutes() {
        return bufferMinutes;
    }
}
