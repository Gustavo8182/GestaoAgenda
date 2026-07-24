package br.com.agendaplatform.relationships.domain;

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
@Table(name = "relationship_contacts")
public class RelationshipContact {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "origin")
    private String origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RelationshipStatus status;

    @Column(name = "last_interaction_at", nullable = false)
    private Instant lastInteractionAt;

    @Column(name = "next_action")
    private String nextAction;

    @Column(name = "next_action_at")
    private Instant nextActionAt;

    @Column(name = "responsible_user_id", nullable = false)
    private UUID responsibleUserId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RelationshipContact() {
    }

    public RelationshipContact(
            UUID organizationId, String name, String phone, String origin, UUID responsibleUserId, Instant now) {
        if (name == null || name.isBlank()) {
            throw new InvalidRelationshipContactException("O nome é obrigatório.");
        }
        if (phone == null || phone.isBlank()) {
            throw new InvalidRelationshipContactException("O telefone é obrigatório.");
        }

        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name.trim();
        this.phone = phone.trim();
        this.origin = (origin == null || origin.isBlank()) ? null : origin.trim();
        this.status = RelationshipStatus.NEW_CONTACT;
        this.lastInteractionAt = now;
        this.responsibleUserId = responsibleUserId;
    }

    public void updateStatus(RelationshipStatus newStatus, Instant now) {
        this.status = newStatus;
        this.lastInteractionAt = now;
    }

    public void reassign(UUID newResponsibleUserId, Instant now) {
        this.responsibleUserId = newResponsibleUserId;
        this.lastInteractionAt = now;
    }

    public void updateNextAction(String nextAction, Instant nextActionAt, Instant now) {
        this.nextAction = (nextAction == null || nextAction.isBlank()) ? null : nextAction.trim();
        this.nextActionAt = nextActionAt;
        this.lastInteractionAt = now;
    }

    public void convert(UUID clientId, UUID appointmentId, Instant now) {
        if (status == RelationshipStatus.DO_NOT_CONTACT) {
            throw new RelationshipContactNotConvertibleException("Este contato está marcado como \"não contatar\".");
        }
        if (this.appointmentId != null) {
            throw new RelationshipContactNotConvertibleException("Este contato já foi convertido em agendamento.");
        }

        this.clientId = clientId;
        this.appointmentId = appointmentId;
        this.status = RelationshipStatus.SCHEDULED;
        this.lastInteractionAt = now;
    }

    public boolean isPendingContact(Instant now) {
        return nextActionAt != null
                && !nextActionAt.isAfter(now)
                && status != RelationshipStatus.SCHEDULED
                && status != RelationshipStatus.DID_NOT_SCHEDULE
                && status != RelationshipStatus.DO_NOT_CONTACT;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getOrigin() {
        return origin;
    }

    public RelationshipStatus getStatus() {
        return status;
    }

    public Instant getLastInteractionAt() {
        return lastInteractionAt;
    }

    public String getNextAction() {
        return nextAction;
    }

    public Instant getNextActionAt() {
        return nextActionAt;
    }

    public UUID getResponsibleUserId() {
        return responsibleUserId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
