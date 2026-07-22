package br.com.agendaplatform.availability.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "blocks")
public class Block {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "reason", nullable = false)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Block() {
    }

    public Block(UUID organizationId, Instant startAt, Instant endAt, String reason) {
        if (!endAt.isAfter(startAt)) {
            throw new InvalidBlockException("O horário final deve ser depois do horário inicial.");
        }

        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getReason() {
        return reason;
    }
}
