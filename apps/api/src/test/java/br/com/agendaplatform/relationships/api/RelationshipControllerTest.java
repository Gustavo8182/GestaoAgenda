package br.com.agendaplatform.relationships.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.authenticatedPost;
import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;

import br.com.agendaplatform.relationships.domain.RelationshipStatus;
import br.com.agendaplatform.support.IntegrationTestSupport.AuthenticatedSession;
import java.time.Instant;
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
class RelationshipControllerTest {

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
        jdbcTemplate.update("DELETE FROM relationship_contacts");
        jdbcTemplate.update("DELETE FROM appointments");
        jdbcTemplate.update("DELETE FROM clients");
        jdbcTemplate.update("DELETE FROM services");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/relationships")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCreationWithBlankNameOrPhone() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/relationships", auth)
                        .content(objectMapper.writeValueAsString(new CreateRelationshipContactRequest("", "", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsContactAsNewContactWithResponsibleResolvedAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/relationships", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateRelationshipContactRequest("Fulana de Tal", "21999999999", "Instagram"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fulana de Tal"))
                .andExpect(jsonPath("$.origin").value("Instagram"))
                .andExpect(jsonPath("$.status").value("NEW_CONTACT"))
                .andExpect(jsonPath("$.responsibleName").value("Usuária de teste"))
                .andExpect(jsonPath("$.pendingContact").value(false));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'RELATIONSHIP_CONTACT_CREATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/relationships").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void updatesStatusAndNextActionAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID contactId = createContact(auth, "Fulana de Tal", "21999999999");

        mockMvc.perform(authenticatedPost("/api/v1/relationships/" + contactId + "/update", auth)
                        .content(objectMapper.writeValueAsString(new UpdateRelationshipContactRequest(
                                RelationshipStatus.AWAITING_RESPONSE, "Ligar amanhã", Instant.parse("2026-08-02T12:00:00Z")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_RESPONSE"))
                .andExpect(jsonPath("$.nextAction").value("Ligar amanhã"));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'RELATIONSHIP_CONTACT_UPDATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void flagsPendingContactWhenNextActionIsDue() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID contactId = createContact(auth, "Fulana de Tal", "21999999999");

        mockMvc.perform(authenticatedPost("/api/v1/relationships/" + contactId + "/update", auth)
                        .content(objectMapper.writeValueAsString(new UpdateRelationshipContactRequest(
                                null, "Ligar", Instant.parse("2020-01-01T00:00:00Z")))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/relationships").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pendingContact").value(true));
    }

    @Test
    void convertsContactIntoClientAndAppointment() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID contactId = createContact(auth, "Fulana de Tal", "21999999999");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/relationships/" + contactId + "/convert", auth)
                        .content(objectMapper.writeValueAsString(new ConvertRelationshipContactRequest(
                                serviceId, Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Fulana de Tal"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(get("/api/v1/relationships").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].clientId").isNotEmpty())
                .andExpect(jsonPath("$[0].appointmentId").isNotEmpty());

        Long clientCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clients", Long.class);
        assertThat(clientCount).isEqualTo(1L);

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'RELATIONSHIP_CONTACT_CONVERTED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void convertFailsWhenMarkedDoNotContact() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID contactId = createContact(auth, "Fulana de Tal", "21999999999");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/relationships/" + contactId + "/update", auth)
                        .content(objectMapper.writeValueAsString(
                                new UpdateRelationshipContactRequest(RelationshipStatus.DO_NOT_CONTACT, null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedPost("/api/v1/relationships/" + contactId + "/convert", auth)
                        .content(objectMapper.writeValueAsString(new ConvertRelationshipContactRequest(
                                serviceId, Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:30:00Z")))))
                .andExpect(status().isBadRequest());

        Long clientCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clients", Long.class);
        assertThat(clientCount).isEqualTo(0L);
    }

    @Test
    void convertFailsWhenChosenSlotConflictsAndDoesNotCreateAClient() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID contactId = createContact(auth, "Fulana de Tal", "21999999999");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);
        UUID existingClientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO clients (id, organization_id, name, phone, phone_normalized) VALUES (?, ?, ?, ?, ?)",
                existingClientId, auth.organizationId(), "Outra Cliente", "21988888888", "21988888888");
        mockMvc.perform(authenticatedPost("/api/v1/appointments", auth)
                        .content("{\"clientId\":\"" + existingClientId + "\",\"serviceId\":\"" + serviceId + "\","
                                + "\"startAt\":\"2026-08-05T10:00:00Z\",\"endAt\":\"2026-08-05T10:30:00Z\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(authenticatedPost("/api/v1/relationships/" + contactId + "/convert", auth)
                        .content(objectMapper.writeValueAsString(new ConvertRelationshipContactRequest(
                                serviceId, Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:30:00Z")))))
                .andExpect(status().isConflict());

        Long clientCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clients", Long.class);
        assertThat(clientCount).isEqualTo(1L);
    }

    @Test
    void doesNotLeakRelationshipContactsBetweenOrganizations() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");
        createContact(ownerA, "Cliente A", "21999999999");

        mockMvc.perform(get("/api/v1/relationships").session(ownerB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private UUID createContact(AuthenticatedSession auth, String name, String phone) throws Exception {
        MvcResult result = mockMvc.perform(authenticatedPost("/api/v1/relationships", auth)
                        .content(objectMapper.writeValueAsString(new CreateRelationshipContactRequest(name, phone, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
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
