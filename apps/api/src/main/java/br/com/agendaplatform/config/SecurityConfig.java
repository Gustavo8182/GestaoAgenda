package br.com.agendaplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, SecurityContextRepository securityContextRepository, SessionRegistry sessionRegistry)
            throws Exception {
        ConcurrentSessionFilter concurrentSessionFilter = new ConcurrentSessionFilter(
                sessionRegistry, event -> event.getResponse().sendError(HttpServletResponse.SC_UNAUTHORIZED));
        concurrentSessionFilter.setLogoutHandlers(List.of(new SecurityContextLogoutHandler()));

        return http.authorizeHttpRequests(
                        authorize -> authorize
                                .requestMatchers("/api/v1/system/status", "/actuator/health")
                                .permitAll()
                                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/password-reset/**")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(concurrentSessionFilter, SecurityContextHolderFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Registro em memória de qual usuária está em qual sessão HTTP — usado para revogar sessões
     * ativas quando a senha é redefinida (ver {@code PasswordResetService}) e para a proteção
     * contra fixação de sessão abaixo.
     */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Troca o ID da sessão HTTP no momento em que ela vira autenticada, para impedir fixação de
     * sessão (uma sessão pré-autenticação, plantada por um atacante, não pode virar autenticada
     * ao ser reaproveitada no login), e registra a sessão no {@link SessionRegistry}. Aplicado
     * manualmente pelo login customizado — o filtro padrão do Spring Security que faria isso
     * automaticamente não participa desse fluxo.
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        return new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Força a materialização do token CSRF (carregamento adiado) para que o cookie
     * XSRF-TOKEN seja sempre enviado, mesmo sem nenhuma view server-side lendo o token.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
