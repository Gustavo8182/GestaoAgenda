package br.com.agendaplatform.clients.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.agendaplatform.identity.api.LoginRequest;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ClientControllerTest {

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
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM clients");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCreationWithBlankNameOrInvalidPhone() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest("", "abc"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPhoneThatDoesNotHaveEnoughDigits() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest("Fulana", "999"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsClientAndRecordsAuditWithoutDuplicateWarning() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "(21) 99999-9999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.name").value("Fulana de Tal"))
                .andExpect(jsonPath("$.possibleDuplicate").value(false));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'CLIENT_CREATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/clients").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Fulana de Tal"));
    }

    @Test
    void flagsPossibleDuplicateForDifferentlyFormattedSamePhoneInSameOrganization() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "(21) 99999-9999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(false));

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana da Silva", "+55 21 99999-9999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(true));

        mockMvc.perform(get("/api/v1/clients").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsInvalidAlternatePhone() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "21999999999", "123", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsClientWithAlternatePhoneOriginAndNotes() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest(
                                "Fulana de Tal",
                                "(21) 99999-9999",
                                "(21) 98888-7777",
                                "Indicação de amiga",
                                "Prefere horários pela manhã"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.alternatePhone").value("(21) 98888-7777"))
                .andExpect(jsonPath("$.client.origin").value("Indicação de amiga"))
                .andExpect(jsonPath("$.client.notes").value("Prefere horários pela manhã"));
    }

    @Test
    void flagsPossibleDuplicateWhenAlternatePhoneMatchesAnExistingPrimaryPhone() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "21999999999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(false));

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest(
                                "Fulana da Silva", "21988887777", "21999999999", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(true));
    }

    @Test
    void searchFindsClientsByPartialNameOrPhoneDigits() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "(21) 99999-1234"))))
                .andExpect(status().isCreated());
        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Beltrana Souza", "(21) 97777-5678"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/clients").session(auth.session()).param("query", "beltra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Beltrana Souza"));

        mockMvc.perform(get("/api/v1/clients").session(auth.session()).param("query", "1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Fulana de Tal"));

        mockMvc.perform(get("/api/v1/clients").session(auth.session()).param("query", "não existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void doesNotFlagDuplicateOrLeakClientsBetweenOrganizations() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", ownerA)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana da Organização A", "21999999999"))))
                .andExpect(status().isCreated());

        mockMvc.perform(authenticatedPost("/api/v1/clients", ownerB)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana da Organização B", "21999999999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(false));

        mockMvc.perform(get("/api/v1/clients").session(ownerB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Fulana da Organização B"));
    }

    private MockHttpServletRequestBuilder authenticatedPost(String url, AuthenticatedSession auth) {
        return post(url)
                .session(auth.session())
                .cookie(auth.csrfCookie())
                .header("X-XSRF-TOKEN", auth.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
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

        Cookie csrfCookie = fetchCsrfCookie();
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        return new AuthenticatedSession(session, csrfCookie);
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private record AuthenticatedSession(MockHttpSession session, Cookie csrfCookie) {
    }
}
