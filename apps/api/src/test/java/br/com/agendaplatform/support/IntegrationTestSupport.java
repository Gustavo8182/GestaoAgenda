package br.com.agendaplatform.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.agendaplatform.identity.api.LoginRequest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Reúne o boilerplate repetido em todo teste de controller que precisa de uma organização
 * autenticada: criar usuária + organização + vínculo direto no banco (mais rápido que passar
 * por um endpoint de cadastro público, que não existe), obter o cookie CSRF e logar via
 * /api/v1/auth/login. Extraído depois que a mesma lógica apareceu, praticamente idêntica,
 * em seis classes de teste diferentes.
 */
public final class IntegrationTestSupport {

    public static final String RAW_PASSWORD = "SenhaForte123!";

    private IntegrationTestSupport() {
    }

    public static AuthenticatedSession createOrganizationWithOwner(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            String email)
            throws Exception {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                userId, email, passwordEncoder.encode(RAW_PASSWORD), "Usuária de teste");

        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                organizationId, "Organização de teste", "organizacao-teste-" + organizationId);
        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, role, status) VALUES (?, ?, 'OWNER', 'ACTIVE')",
                organizationId, userId);

        Cookie csrfCookie = fetchCsrfCookie(mockMvc);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        return new AuthenticatedSession(organizationId, session, csrfCookie);
    }

    public static AuthenticatedSession addMemberAndLogin(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            UUID organizationId,
            String role,
            String email)
            throws Exception {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                userId, email, passwordEncoder.encode(RAW_PASSWORD), "Usuária de teste");
        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, role, status) VALUES (?, ?, ?, 'ACTIVE')",
                organizationId, userId, role);

        Cookie csrfCookie = fetchCsrfCookie(mockMvc);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        return new AuthenticatedSession(organizationId, session, csrfCookie);
    }

    public static Cookie fetchCsrfCookie(MockMvc mockMvc) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    public static MockHttpServletRequestBuilder authenticatedPost(String url, AuthenticatedSession auth) {
        return post(url)
                .session(auth.session())
                .cookie(auth.csrfCookie())
                .header("X-XSRF-TOKEN", auth.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    public record AuthenticatedSession(UUID organizationId, MockHttpSession session, Cookie csrfCookie) {
    }
}
