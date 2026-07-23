package br.com.agendaplatform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

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

    protected PasswordResetToken() {
    }

    public PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Mesma mensagem genérica tanto para "já usado" quanto para "expirado" — não é dado
     * relevante para quem está tentando redefinir a senha, só ajudaria alguém tentando abusar
     * de um token roubado ou de força bruta a entender por que falhou.
     */
    public void markUsed(Instant now) {
        if (usedAt != null || expiresAt.isBefore(now)) {
            throw new InvalidPasswordResetTokenException("Link de redefinição inválido ou expirado.");
        }
        this.usedAt = now;
    }

    /**
     * Chamado quando um novo pedido de redefinição é feito para a mesma usuária — invalida
     * silenciosamente um token anterior ainda pendente, sem lançar erro mesmo se já estiver
     * expirado (diferente de markUsed, que precisa distinguir "válido" de "inválido" no momento
     * da confirmação). Um link antigo não pode continuar aceito depois que um novo foi emitido.
     */
    public void invalidate(Instant now) {
        if (usedAt == null) {
            this.usedAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }
}
