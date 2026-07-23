package br.com.agendaplatform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_invitation_tokens")
public class UserInvitationToken {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserInvitationToken() {
    }

    public UserInvitationToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Mesma mensagem genérica tanto para "já usado" quanto para "expirado" — não é dado
     * relevante para quem está tentando aceitar o convite.
     */
    public void markUsed(Instant now) {
        if (usedAt != null || expiresAt.isBefore(now)) {
            throw new InvalidUserInvitationException("Convite inválido ou expirado.");
        }
        this.usedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }
}
