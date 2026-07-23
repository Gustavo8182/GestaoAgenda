package br.com.agendaplatform.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.addMemberAndLogin;
import static br.com.agendaplatform.support.IntegrationTestSupport.authenticatedPost;
import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;

import br.com.agendaplatform.support.IntegrationTestSupport.AuthenticatedSession;
import java.sql.Timestamp;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ExportControllerTest {

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
        jdbcTemplate.update("DELETE FROM waitlist_entries");
        jdbcTemplate.update("DELETE FROM appointments");
        jdbcTemplate.update("DELETE FROM clients");
        jdbcTemplate.update("DELETE FROM services");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportsClientsAsCsvAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        createClient(auth.organizationId(), "Fulana de Tal", "21999999999");

        String csv = mockMvc.perform(get("/api/v1/reports/export/clients").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"clientes.csv\""))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).startsWith("Nome,Telefone,Telefone alternativo,Origem,Observações\r\n");
        assertThat(csv).contains("Fulana de Tal,21999999999");

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'CLIENTS_EXPORTED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void exportsAppointmentsAsCsv() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal", "21999999999");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);
        createAppointment(
                auth.organizationId(),
                clientId,
                serviceId,
                Instant.parse("2026-08-05T10:00:00Z"),
                Instant.parse("2026-08-05T10:30:00Z"));

        String csv = mockMvc.perform(get("/api/v1/reports/export/appointments").session(auth.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).contains("Fulana de Tal,Corte,2026-08-05T10:00:00Z,2026-08-05T10:30:00Z,SCHEDULED");

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENTS_EXPORTED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void exportsWaitlistAsCsv() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal", "21999999999");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/waitlist", auth)
                        .content("{\"clientId\":\"" + clientId + "\",\"serviceId\":\"" + serviceId + "\","
                                + "\"preferredStartDate\":\"2026-08-01\",\"preferredEndDate\":\"2026-08-15\","
                                + "\"preferredStartTime\":\"09:00:00\",\"preferredEndTime\":\"12:00:00\","
                                + "\"priority\":\"NORMAL\",\"expiresAt\":\"2026-09-01T00:00:00Z\"}"))
                .andExpect(status().isCreated());

        String csv = mockMvc.perform(get("/api/v1/reports/export/waitlist").session(auth.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).contains("Fulana de Tal,Corte,2026-08-01,2026-08-15,09:00,12:00,NORMAL");

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'WAITLIST_EXPORTED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void exportsRelationshipsAsCsv() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/relationships", auth)
                        .content("{\"name\":\"Fulana de Tal\",\"phone\":\"21999999999\",\"origin\":\"Instagram\"}"))
                .andExpect(status().isCreated());

        String csv = mockMvc.perform(get("/api/v1/reports/export/relationships").session(auth.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).contains("Fulana de Tal,21999999999,Instagram,NEW_CONTACT");

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'RELATIONSHIPS_EXPORTED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void doesNotLeakDataBetweenOrganizations() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");
        createClient(ownerA.organizationId(), "Cliente da Organização A", "21999999999");

        String csv = mockMvc.perform(get("/api/v1/reports/export/clients").session(ownerB.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).doesNotContain("Cliente da Organização A");
    }

    private UUID createClient(UUID organizationId, String name, String phone) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO clients (id, organization_id, name, phone, phone_normalized) VALUES (?, ?, ?, ?, ?)",
                id, organizationId, name, phone, phone);
        return id;
    }

    private UUID createService(UUID organizationId, String name, int durationMinutes) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO services (id, organization_id, name, duration_minutes) VALUES (?, ?, ?, ?)",
                id, organizationId, name, durationMinutes);
        return id;
    }

    private void createAppointment(UUID organizationId, UUID clientId, UUID serviceId, Instant startAt, Instant endAt) {
        jdbcTemplate.update(
                "INSERT INTO appointments (id, organization_id, client_id, service_id, start_at, end_at, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'SCHEDULED')",
                UUID.randomUUID(), organizationId, clientId, serviceId, Timestamp.from(startAt), Timestamp.from(endAt));
    }

    @Test
    void deniesAllExportsToSecretary() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession secretary = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SECRETARY", "secretaria@exemplo.test");

        mockMvc.perform(get("/api/v1/reports/export/clients").session(secretary.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/export/appointments").session(secretary.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/export/waitlist").session(secretary.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/export/relationships").session(secretary.session()))
                .andExpect(status().isForbidden());
    }

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
        return createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, email);
    }
}
