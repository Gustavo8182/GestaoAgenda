package br.com.agendaplatform.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.agendaplatform.identity.api.LoginRequest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
class AppointmentControllerTest {

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
        mockMvc.perform(get("/api/v1/appointments")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCreationWithUnknownClientOrService() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T11:00:00Z")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCreationWhenEndIsNotAfterStart() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:00:00Z")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsAppointmentAndRecordsAudit() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Fulana de Tal"))
                .andExpect(jsonPath("$.serviceName").value("Corte"));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENT_CREATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/appointments").session(org.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientName").value("Fulana de Tal"));
    }

    @Test
    void rejectsOverlappingAppointmentAtApplicationLevel() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T11:00:00Z")))))
                .andExpect(status().isCreated());

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:30:00Z"),
                                Instant.parse("2026-08-01T11:30:00Z")))))
                .andExpect(status().isConflict());
    }

    @Test
    void doesNotLeakAppointmentsBetweenOrganizationsAndAllowsSameSlotInDifferentOrganizations() throws Exception {
        Organization orgA = createOrganizationWithOwner("dona-a@exemplo.test");
        Organization orgB = createOrganizationWithOwner("dona-b@exemplo.test");
        UUID clientA = createClient(orgA.organizationId(), "Cliente da Organização A");
        UUID serviceA = createService(orgA.organizationId(), "Corte", 30);
        UUID clientB = createClient(orgB.organizationId(), "Cliente da Organização B");
        UUID serviceB = createService(orgB.organizationId(), "Corte", 30);

        Instant start = Instant.parse("2026-08-01T10:00:00Z");
        Instant end = Instant.parse("2026-08-01T11:00:00Z");

        mockMvc.perform(authenticatedPost("/api/v1/appointments", orgA)
                        .content(objectMapper.writeValueAsString(
                                new CreateAppointmentRequest(clientA, serviceA, start, end))))
                .andExpect(status().isCreated());

        mockMvc.perform(authenticatedPost("/api/v1/appointments", orgB)
                        .content(objectMapper.writeValueAsString(
                                new CreateAppointmentRequest(clientB, serviceB, start, end))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/appointments").session(orgB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientName").value("Cliente da Organização B"));
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
