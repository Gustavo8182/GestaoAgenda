package br.com.agendaplatform.waitlist.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.addMemberAndLogin;
import static br.com.agendaplatform.support.IntegrationTestSupport.authenticatedPost;
import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;

import br.com.agendaplatform.support.IntegrationTestSupport.AuthenticatedSession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
class WaitlistControllerTest {

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
        mockMvc.perform(get("/api/v1/waitlist")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCreationWithUnknownClientOrService() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/waitlist", auth)
                        .content(objectMapper.writeValueAsString(new CreateWaitlistEntryRequest(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 15),
                                LocalTime.of(9, 0),
                                LocalTime.of(12, 0),
                                br.com.agendaplatform.waitlist.domain.WaitlistPriority.NORMAL,
                                Instant.parse("2026-09-01T00:00:00Z")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCreationWithExpiryInThePast() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/waitlist", auth)
                        .content(objectMapper.writeValueAsString(new CreateWaitlistEntryRequest(
                                clientId,
                                serviceId,
                                LocalDate.of(2020, 8, 1),
                                LocalDate.of(2020, 8, 15),
                                LocalTime.of(9, 0),
                                LocalTime.of(12, 0),
                                br.com.agendaplatform.waitlist.domain.WaitlistPriority.NORMAL,
                                Instant.parse("2020-09-01T00:00:00Z")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsEntryListsItAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/waitlist", auth)
                        .content(objectMapper.writeValueAsString(new CreateWaitlistEntryRequest(
                                clientId,
                                serviceId,
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 15),
                                LocalTime.of(9, 0),
                                LocalTime.of(12, 0),
                                br.com.agendaplatform.waitlist.domain.WaitlistPriority.HIGH,
                                Instant.parse("2026-09-01T00:00:00Z")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Fulana de Tal"))
                .andExpect(jsonPath("$.serviceName").value("Corte"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("WAITING"));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'WAITLIST_ENTRY_CREATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/waitlist").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientName").value("Fulana de Tal"));
    }

    @Test
    void ordersListByPriorityThenCreationOrder() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);

        createEntry(auth, clientId, serviceId, "NORMAL");
        createEntry(auth, clientId, serviceId, "HIGH");
        createEntry(auth, clientId, serviceId, "LOW");

        mockMvc.perform(get("/api/v1/waitlist").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[1].priority").value("NORMAL"))
                .andExpect(jsonPath("$[2].priority").value("LOW"));
    }

    @Test
    void cancelsEntryAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);
        UUID entryId = createEntry(auth, clientId, serviceId, "NORMAL");

        mockMvc.perform(authenticatedPost("/api/v1/waitlist/" + entryId + "/cancel", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'WAITLIST_ENTRY_CANCELLED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(authenticatedPost("/api/v1/waitlist/" + entryId + "/cancel", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convertsEntryIntoAppointmentAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);
        UUID entryId = createEntry(auth, clientId, serviceId, "NORMAL");

        mockMvc.perform(authenticatedPost("/api/v1/waitlist/" + entryId + "/convert", auth)
                        .content(objectMapper.writeValueAsString(new ConvertWaitlistEntryRequest(
                                Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Fulana de Tal"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(get("/api/v1/waitlist").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONVERTED"))
                .andExpect(jsonPath("$[0].appointmentId").isNotEmpty());

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'WAITLIST_ENTRY_CONVERTED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        Long appointmentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments", Long.class);
        assertThat(appointmentCount).isEqualTo(1L);
    }

    @Test
    void conversionFailsWhenChosenSlotConflictsWithAnExistingAppointment() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);
        UUID entryId = createEntry(auth, clientId, serviceId, "NORMAL");

        mockMvc.perform(authenticatedPost("/api/v1/appointments", auth)
                        .content("{\"clientId\":\"" + clientId + "\",\"serviceId\":\"" + serviceId + "\","
                                + "\"startAt\":\"2026-08-05T10:00:00Z\",\"endAt\":\"2026-08-05T10:30:00Z\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(authenticatedPost("/api/v1/waitlist/" + entryId + "/convert", auth)
                        .content(objectMapper.writeValueAsString(new ConvertWaitlistEntryRequest(
                                Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:30:00Z")))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/waitlist").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WAITING"));
    }

    @Test
    void findsCompatibleEntriesForACandidateSlot() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);
        UUID otherServiceId = createService(auth.organizationId(), "Escova", 45);

        createEntry(auth, clientId, serviceId, "NORMAL");
        createEntry(auth, clientId, otherServiceId, "NORMAL");

        // Organização usa America/Sao_Paulo (UTC-3) por padrão: 13:00Z = 10:00 local,
        // dentro da janela preferida (09:00-12:00 local).
        mockMvc.perform(get("/api/v1/waitlist/compatible")
                        .session(auth.session())
                        .param("serviceId", serviceId.toString())
                        .param("startAt", "2026-08-05T13:00:00Z")
                        .param("endAt", "2026-08-05T13:30:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("Corte"));

        mockMvc.perform(get("/api/v1/waitlist/compatible")
                        .session(auth.session())
                        .param("serviceId", serviceId.toString())
                        .param("startAt", "2026-08-20T13:00:00Z")
                        .param("endAt", "2026-08-20T13:30:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void doesNotLeakWaitlistEntriesBetweenOrganizations() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");
        UUID clientA = createClient(ownerA.organizationId(), "Cliente A");
        UUID serviceA = createService(ownerA.organizationId(), "Corte", 30);
        createEntry(ownerA, clientA, serviceA, "NORMAL");

        mockMvc.perform(get("/api/v1/waitlist").session(ownerB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deniesWaitlistCreationAndListingToSupport() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(owner.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(owner.organizationId(), "Corte", 30);
        AuthenticatedSession support = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SUPPORT", "suporte@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/waitlist", support)
                        .content(objectMapper.writeValueAsString(new CreateWaitlistEntryRequest(
                                clientId,
                                serviceId,
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 15),
                                LocalTime.of(9, 0),
                                LocalTime.of(12, 0),
                                br.com.agendaplatform.waitlist.domain.WaitlistPriority.NORMAL,
                                Instant.parse("2026-09-01T00:00:00Z")))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/waitlist").session(support.session())).andExpect(status().isForbidden());
    }

    private UUID createEntry(AuthenticatedSession auth, UUID clientId, UUID serviceId, String priority)
            throws Exception {
        MvcResult result = mockMvc.perform(authenticatedPost("/api/v1/waitlist", auth)
                        .content(objectMapper.writeValueAsString(new CreateWaitlistEntryRequest(
                                clientId,
                                serviceId,
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 15),
                                LocalTime.of(9, 0),
                                LocalTime.of(12, 0),
                                br.com.agendaplatform.waitlist.domain.WaitlistPriority.valueOf(priority),
                                Instant.parse("2026-09-01T00:00:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
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
