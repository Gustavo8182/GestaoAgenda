package br.com.agendaplatform.identity.application;

import br.com.agendaplatform.identity.domain.InvalidPasswordResetTokenException;
import br.com.agendaplatform.identity.domain.PasswordResetToken;
import br.com.agendaplatform.identity.domain.User;
import br.com.agendaplatform.identity.domain.UserStatus;
import br.com.agendaplatform.identity.infrastructure.PasswordResetTokenRepository;
import br.com.agendaplatform.identity.infrastructure.UserRepository;
import br.com.agendaplatform.shared.security.SessionRevoker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SessionRevoker sessionRevoker;
    private final Clock clock;
    private final String frontendUrl;
    private final Duration tokenValidity;

    PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            SessionRevoker sessionRevoker,
            Clock clock,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.password-reset.token-validity-minutes:60}") long tokenValidityMinutes) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.sessionRevoker = sessionRevoker;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
        this.tokenValidity = Duration.ofMinutes(tokenValidityMinutes);
    }

    /**
     * Sempre "silencioso": se o e-mail não existir ou a conta não estiver ativa, não faz nada
     * — quem chama este método sempre deve responder com a mesma mensagem genérica de sucesso,
     * para não revelar se um e-mail está cadastrado.
     */
    @Transactional
    public void requestReset(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        Instant now = clock.instant();
        passwordResetTokenRepository
                .findAllByUserIdAndUsedAtIsNull(user.getId())
                .forEach(pendingToken -> pendingToken.invalidate(now));

        String rawToken = SecureTokenGenerator.generateRawToken();
        Instant expiresAt = now.plus(tokenValidity);
        passwordResetTokenRepository.save(
                new PasswordResetToken(user.getId(), SecureTokenGenerator.hash(rawToken), expiresAt));

        sendResetEmail(user.getEmail(), rawToken);
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(SecureTokenGenerator.hash(rawToken))
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Link de redefinição inválido ou expirado."));

        token.markUsed(clock.instant());

        User user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Link de redefinição inválido ou expirado."));
        user.changePassword(passwordEncoder.encode(newPassword));

        sessionRevoker.revokeSessionsFor(user.getEmail());
    }

    private void sendResetEmail(String email, String rawToken) {
        String link = frontendUrl + "/redefinir-senha?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Redefinição de senha");
        message.setText(
                "Para redefinir sua senha, acesse o link abaixo:\n\n"
                        + link
                        + "\n\nEste link expira em "
                        + tokenValidity.toMinutes()
                        + " minutos e só pode ser usado uma vez. "
                        + "Se você não pediu essa redefinição, ignore este e-mail.");
        mailSender.send(message);
    }
}
