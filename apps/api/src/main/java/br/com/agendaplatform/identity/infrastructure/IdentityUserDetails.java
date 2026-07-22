package br.com.agendaplatform.identity.infrastructure;

import br.com.agendaplatform.shared.security.AuthenticatedPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

final class IdentityUserDetails implements UserDetails, AuthenticatedPrincipal {

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;

    IdentityUserDetails(UUID userId, String email, String passwordHash, boolean enabled) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    @Override
    public UUID userId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
