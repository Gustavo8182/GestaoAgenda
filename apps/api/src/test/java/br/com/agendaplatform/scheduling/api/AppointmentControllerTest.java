package br.com.agendaplatform.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.agendaplatform.identity.api.LoginRequest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
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
        jdbcTemplate.update("DELETE FROM blocks");
        jdbcTemplate.update("DELETE FROM business_hours");
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
    void reschedulesAppointmentAndRecordsAuditWithPreviousAndNewTimes() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID appointmentId = UUID.fromString(objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T14:00:00Z"), Instant.parse("2026-08-01T14:30:00Z")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value("2026-08-01T14:00:00Z"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENT_RESCHEDULED' "
                        + "AND metadata->>'previousStartAt' = '2026-08-01T10:00:00Z' "
                        + "AND metadata->>'newStartAt' = '2026-08-01T14:00:00Z'",
                Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void rejectsRescheduleToAnOverlappingSlot() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated());

        MvcResult secondResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T14:00:00Z"),
                                Instant.parse("2026-08-01T14:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID secondId = UUID.fromString(objectMapper
                .readTree(secondResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + secondId + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T10:15:00Z"), Instant.parse("2026-08-01T10:45:00Z")))))
                .andExpect(status().isConflict());
    }

    @Test
    void rescheduleOfUnknownAppointmentReturnsNotFound() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + UUID.randomUUID() + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelsAppointmentRecordsReasonAndFreesUpTheSlotForANewAppointment() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID appointmentId = UUID.fromString(objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/cancel", org)
                        .content("{\"reason\":\"Cliente remarcou por telefone.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Cliente remarcou por telefone."));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENT_CANCELLED' "
                        + "AND metadata->>'reason' = 'Cliente remarcou por telefone.'",
                Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelRequiresAReason() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID appointmentId = UUID.fromString(objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/cancel", org)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancellingAnAlreadyCancelledAppointmentIsRejected() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID appointmentId = UUID.fromString(objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/cancel", org)
                        .content("{\"reason\":\"Motivo qualquer.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/cancel", org)
                        .content("{\"reason\":\"Outro motivo.\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotRescheduleACancelledAppointment() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID appointmentId = UUID.fromString(objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/cancel", org)
                        .content("{\"reason\":\"Motivo qualquer.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T15:00:00Z"), Instant.parse("2026-08-01T15:30:00Z")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmsAppointmentAndRecordsAudit() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        UUID appointmentId = createScheduledAppointment(
                org, clientId, serviceId, Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z"));

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/confirm", org))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENT_CONFIRMED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/confirm", org))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registersArrivalFromScheduledOrConfirmedAndThenStartsAndCompletesService() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        UUID appointmentId = createScheduledAppointment(
                org, clientId, serviceId, Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z"));

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/start", org))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/arrive", org))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/start", org))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/complete", org))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        Long completedAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENT_COMPLETED'", Long.class);
        assertThat(completedAuditCount).isEqualTo(1L);
    }

    @Test
    void marksNoShowAndFreesUpTheSlotForANewAppointment() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        UUID appointmentId = createScheduledAppointment(
                org, clientId, serviceId, Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z"));

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/no-show", org))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENT_NO_SHOW'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated());
    }

    @Test
    void cannotCancelACompletedAppointment() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        UUID appointmentId = createScheduledAppointment(
                org, clientId, serviceId, Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z"));

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/arrive", org))
                .andExpect(status().isOk());
        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/complete", org))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/cancel", org)
                        .content("{\"reason\":\"Tentativa tardia.\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotRescheduleAnArrivedAppointment() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        UUID appointmentId = createScheduledAppointment(
                org, clientId, serviceId, Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z"));

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/arrive", org))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T15:00:00Z"), Instant.parse("2026-08-01T15:30:00Z")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAppointmentOutsideConfiguredBusinessHours() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        // 2026-08-01 é sábado; a organização só funciona à tarde nesse dia.
        createBusinessHours(org.organizationId(), "SATURDAY", "14:00:00", "18:00:00");

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowsAppointmentWithinConfiguredBusinessHours() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        createBusinessHours(org.organizationId(), "SATURDAY", "06:00:00", "20:00:00");

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAppointmentOverlappingBlock() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        createBlock(
                org.organizationId(),
                Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-01T11:00:00Z"),
                "Feriado");

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:15:00Z"),
                                Instant.parse("2026-08-01T10:45:00Z")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("blocked_time"));
    }

    @Test
    void rejectsRescheduleIntoBlockedTime() throws Exception {
        Organization org = createOrganizationWithOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID appointmentId = UUID.fromString(objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        createBlock(
                org.organizationId(),
                Instant.parse("2026-08-01T14:00:00Z"),
                Instant.parse("2026-08-01T15:00:00Z"),
                "Compromisso pessoal");

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T14:15:00Z"), Instant.parse("2026-08-01T14:45:00Z")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("blocked_time"));
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

    private UUID createScheduledAppointment(
            Organization org, UUID clientId, UUID serviceId, Instant startAt, Instant endAt) throws Exception {
        MvcResult result = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(
                                new CreateAppointmentRequest(clientId, serviceId, startAt, endAt))))
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

    private void createBusinessHours(UUID organizationId, String dayOfWeek, String startTime, String endTime) {
        jdbcTemplate.update(
                "INSERT INTO business_hours (id, organization_id, day_of_week, start_time, end_time) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                organizationId,
                dayOfWeek,
                java.sql.Time.valueOf(startTime),
                java.sql.Time.valueOf(endTime));
    }

    private void createBlock(UUID organizationId, Instant startAt, Instant endAt, String reason) {
        jdbcTemplate.update(
                "INSERT INTO blocks (id, organization_id, start_at, end_at, reason) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), organizationId, Timestamp.from(startAt), Timestamp.from(endAt), reason);
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
