package br.com.agendaplatform.identity.infrastructure;

import br.com.agendaplatform.identity.domain.User;
import br.com.agendaplatform.identity.domain.UserStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class IdentityUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    IdentityUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("E-mail ou senha inválidos"));

        return new IdentityUserDetails(
                user.getId(), user.getEmail(), user.getPasswordHash(), user.getStatus() == UserStatus.ACTIVE);
    }
}
