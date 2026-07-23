package br.com.agendaplatform.auditing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.addMemberAndLogin;
import static br.com.agendaplatform.support.IntegrationTestSupport.authenticatedPost;
import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;

import br.com.agendaplatform.support.IntegrationTestSupport.AuthenticatedSession;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
class AuditTrailControllerTest {

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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");

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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession orgA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession orgB = loginAsNewOwner("dona-b@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", orgA)
                        .content("{\"name\":\"Cliente da organização A\",\"phone\":\"21999999999\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit-log").session(orgB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deniesAuditLogAccessToSupport() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession support = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SUPPORT", "suporte@exemplo.test");

        mockMvc.perform(get("/api/v1/audit-log").session(support.session())).andExpect(status().isForbidden());
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

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
        return createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, email);
    }
}
