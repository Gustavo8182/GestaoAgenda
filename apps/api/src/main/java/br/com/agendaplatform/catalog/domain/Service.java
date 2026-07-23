package br.com.agendaplatform.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "services")
public class Service {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "color")
    private String color;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "requires_confirmation", nullable = false)
    private boolean requiresConfirmation;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Service() {
    }

    public Service(
            UUID organizationId,
            String name,
            int durationMinutes,
            String color,
            int displayOrder,
            boolean requiresConfirmation,
            int bufferMinutes) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.color = color;
        this.displayOrder = displayOrder;
        this.requiresConfirmation = requiresConfirmation;
        this.active = true;
        this.bufferMinutes = bufferMinutes;
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
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

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getColor() {
        return color;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public boolean isActive() {
        return active;
    }

    public int getBufferMinutes() {
        return bufferMinutes;
    }
}
