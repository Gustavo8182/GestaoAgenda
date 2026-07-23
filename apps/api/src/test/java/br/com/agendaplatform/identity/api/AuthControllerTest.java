package br.com.agendaplatform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerTest {

    private static final String RAW_PASSWORD = "SenhaForte123!";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessToMeWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsLoginWithoutCsrfToken() throws Exception {
        createUser("dona@exemplo.test", RAW_PASSWORD, "ACTIVE");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("dona@exemplo.test", RAW_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsLoginWithWrongPassword() throws Exception {
        createUser("dona@exemplo.test", RAW_PASSWORD, "ACTIVE");
        Cookie csrfCookie = fetchCsrfCookie();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("dona@exemplo.test", "senha-errada"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsLoginForDisabledUser() throws Exception {
        createUser("desativada@exemplo.test", RAW_PASSWORD, "DISABLED");
        Cookie csrfCookie = fetchCsrfCookie();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("desativada@exemplo.test", RAW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesLoginWhenUserHasNoActiveOrganization() throws Exception {
        createUser("sem-organizacao@exemplo.test", RAW_PASSWORD, "ACTIVE");
        Cookie csrfCookie = fetchCsrfCookie();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("sem-organizacao@exemplo.test", RAW_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void logsInAndAllowsCurrentUserLookupThenLogout() throws Exception {
        UUID userId = createUser("dona@exemplo.test", RAW_PASSWORD, "ACTIVE");
        addOrganizationMembership(userId, "OWNER", "Clínica de teste");
        Cookie csrfCookie = fetchCsrfCookie();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("dona@exemplo.test", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("dona@exemplo.test"))
                .andExpect(jsonPath("$.organization.organizationName").value("Clínica de teste"))
                .andExpect(jsonPath("$.organization.role").value("OWNER"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("dona@exemplo.test"))
                .andExpect(jsonPath("$.organization.organizationName").value("Clínica de teste"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesSessionIdOnSuccessfulLogin() throws Exception {
        UUID userId = createUser("dona2@exemplo.test", RAW_PASSWORD, "ACTIVE");
        addOrganizationMembership(userId, "OWNER", "Clínica de teste 2");
        Cookie csrfCookie = fetchCsrfCookie();

        MockHttpSession preAuthSession = new MockHttpSession();
        String originalSessionId = preAuthSession.getId();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .session(preAuthSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("dona2@exemplo.test", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession sessionAfterLogin = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(sessionAfterLogin).isNotNull();
        assertThat(sessionAfterLogin.getId()).isNotEqualTo(originalSessionId);
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private UUID createUser(String email, String rawPassword, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, status) VALUES (?, ?, ?, ?, ?)",
                id,
                email,
                passwordEncoder.encode(rawPassword),
                "Usuária de teste",
                status);
        return id;
    }

    private void addOrganizationMembership(UUID userId, String role, String organizationName) {
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                organizationId,
                organizationName,
                "organizacao-teste-" + organizationId);
        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, role, status) VALUES (?, ?, ?, 'ACTIVE')",
                organizationId,
                userId,
                role);
    }
}
