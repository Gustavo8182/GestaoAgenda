package br.com.agendaplatform.identity.application;

import br.com.agendaplatform.identity.EmailAlreadyRegisteredException;
import br.com.agendaplatform.identity.UserProvisioning;
import br.com.agendaplatform.identity.UserRef;
import br.com.agendaplatform.identity.domain.InvalidUserInvitationException;
import br.com.agendaplatform.identity.domain.User;
import br.com.agendaplatform.identity.domain.UserInvitationToken;
import br.com.agendaplatform.identity.infrastructure.UserInvitationTokenRepository;
import br.com.agendaplatform.identity.infrastructure.UserRepository;
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
public class InvitationService implements UserProvisioning {

    private final UserRepository userRepository;
    private final UserInvitationTokenRepository userInvitationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final String frontendUrl;
    private final Duration tokenValidity;

    InvitationService(
            UserRepository userRepository,
            UserInvitationTokenRepository userInvitationTokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            Clock clock,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.invitation.token-validity-minutes:10080}") long tokenValidityMinutes) {
        this.userRepository = userRepository;
        this.userInvitationTokenRepository = userInvitationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
        this.tokenValidity = Duration.ofMinutes(tokenValidityMinutes);
    }

    @Override
    @Transactional
    public UserRef inviteUser(String email, String displayName, String organizationName) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new EmailAlreadyRegisteredException("Já existe uma usuária cadastrada com este e-mail.");
        }

        String placeholderPasswordHash = passwordEncoder.encode(SecureTokenGenerator.generateRawToken());
        User user = new User(email, placeholderPasswordHash, displayName);
        userRepository.save(user);

        String rawToken = SecureTokenGenerator.generateRawToken();
        Instant expiresAt = clock.instant().plus(tokenValidity);
        userInvitationTokenRepository.save(
                new UserInvitationToken(user.getId(), SecureTokenGenerator.hash(rawToken), expiresAt));
        sendInvitationEmail(email, organizationName, rawToken);

        return new UserRef(user.getId(), user.getDisplayName());
    }

    @Transactional
    public void acceptInvitation(String rawToken, String newPassword) {
        UserInvitationToken token = userInvitationTokenRepository
                .findByTokenHash(SecureTokenGenerator.hash(rawToken))
                .orElseThrow(() -> new InvalidUserInvitationException("Convite inválido ou expirado."));

        token.markUsed(clock.instant());

        User user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new InvalidUserInvitationException("Convite inválido ou expirado."));
        user.acceptInvitation(passwordEncoder.encode(newPassword));
    }

    private void sendInvitationEmail(String email, String organizationName, String rawToken) {
        String link = frontendUrl + "/aceitar-convite?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Convite para " + organizationName);
        message.setText(
                "Você foi convidada para fazer parte de \"" + organizationName + "\" na Agenda Platform.\n\n"
                        + "Para aceitar o convite e definir sua senha, acesse o link abaixo:\n\n"
                        + link
                        + "\n\nEste link expira em "
                        + tokenValidity.toDays()
                        + " dias e só pode ser usado uma vez. "
                        + "Se você não esperava este convite, ignore este e-mail.");
        mailSender.send(message);
    }
}
