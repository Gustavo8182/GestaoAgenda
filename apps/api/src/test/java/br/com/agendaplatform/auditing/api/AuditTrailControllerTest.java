package br.com.agendaplatform.auditing.api;

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
class AuditTrailControllerTest {

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
        jdbcTemplate.update("DELETE FROM appointments");
        jdbcTemplate.update("DELETE FROM clients");
        jdbcTemplate.update("DELETE FROM services");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/audit-log")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsRecentEntriesMostRecentFirstWithActorNameResolved() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", org)
                        .content("{\"name\":\"Fulana de Tal\",\"phone\":\"21999999999\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(authenticatedPost("/api/v1/clients", org)
                        .content("{\"name\":\"Beltrana\",\"phone\":\"21988888888\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit-log").session(org.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("CLIENT_CREATED"))
                .andExpect(jsonPath("$[0].entityType").value("CLIENT"))
                .andExpect(jsonPath("$[0].actorName").value("Usuária de teste"))
                .andExpect(jsonPath("$[1].action").value("CLIENT_CREATED"));
    }

    @Test
    void surfacesMetadataForActionsThatRecordIt() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content("{\"clientId\":\"" + clientId + "\",\"serviceId\":\"" + serviceId + "\","
                                + "\"startAt\":\"2026-08-01T10:00:00Z\",\"endAt\":\"2026-08-01T10:30:00Z\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID appointmentId = UUID.fromString(objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/cancel", org)
                        .content("{\"reason\":\"Cliente desmarcou.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-log").session(org.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("APPOINTMENT_CANCELLED"))
                .andExpect(jsonPath("$[0].metadata.reason").value("Cliente desmarcou."));
    }

    @Test
    void doesNotLeakAuditEntriesBetweenOrganizations() throws Exception {
        Organization orgA = createOrganizationWithOwner("dona-a@exemplo.test");
        Organization orgB = createOrganizationWithOwner("dona-b@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", orgA)
                        .content("{\"name\":\"Cliente da organização A\",\"phone\":\"21999999999\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit-log").session(orgB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private MockHttpServletRequestBuilder authenticatedPost(String url, Organization org) {
        return post(url)
                .session(org.session())
                .cookie(org.csrfCookie())
                .header("X-XSRF-TOKEN", org.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private UUID createClient(UUID organizationId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO clients (id, organization_id, name, phone, phone_normalized) VALUES (?, ?, ?, ?, ?)",
                id, organizationId, name, "21999999999", "21999999999");
        return id;
    }

    private UUID createService(UUID organizationId, String name, int durationMinutes) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO services (id, organization_id, name, duration_minutes) VALUES (?, ?, ?, ?)",
                id, organizationId, name, durationMinutes);
        return id;
    }

    private Organization createOrganizationWithOwner(String email) throws Exception {
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
        return new Organization(organizationId, session, csrfCookie);
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private record Organization(UUID organizationId, MockHttpSession session, Cookie csrfCookie) {
    }
}
