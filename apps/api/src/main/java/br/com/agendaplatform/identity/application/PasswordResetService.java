package br.com.agendaplatform.identity.application;

import br.com.agendaplatform.identity.domain.InvalidPasswordResetTokenException;
import br.com.agendaplatform.identity.domain.PasswordResetToken;
import br.com.agendaplatform.identity.domain.User;
import br.com.agendaplatform.identity.domain.UserStatus;
import br.com.agendaplatform.identity.infrastructure.PasswordResetTokenRepository;
import br.com.agendaplatform.identity.infrastructure.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final String frontendUrl;
    private final Duration tokenValidity;

    PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            Clock clock,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.password-reset.token-validity-minutes:60}") long tokenValidityMinutes) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
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

        String rawToken = generateRawToken();
        Instant expiresAt = clock.instant().plus(tokenValidity);
        passwordResetTokenRepository.save(new PasswordResetToken(user.getId(), hash(rawToken), expiresAt));

        sendResetEmail(user.getEmail(), rawToken);
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Link de redefinição inválido ou expirado."));

        token.markUsed(clock.instant());

        User user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Link de redefinição inválido ou expirado."));
        user.changePassword(passwordEncoder.encode(newPassword));
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

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível.", e);
        }
    }
}
