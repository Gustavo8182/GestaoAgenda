package br.com.agendaplatform.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.addMemberAndLogin;
import static br.com.agendaplatform.support.IntegrationTestSupport.authenticatedPost;
import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;

import br.com.agendaplatform.scheduling.domain.RecurrenceFrequency;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AppointmentControllerTest {

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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");

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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
    void listsOnlyTheAppointmentsOfTheRequestedClientMostRecentFirst() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientA = createClient(org.organizationId(), "Cliente A");
        UUID clientB = createClient(org.organizationId(), "Cliente B");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        UUID olderAppointmentId = createScheduledAppointment(
                org, clientA, serviceId, Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z"));
        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + olderAppointmentId + "/cancel", org)
                        .content("{\"reason\":\"Motivo qualquer.\"}"))
                .andExpect(status().isOk());
        createScheduledAppointment(
                org, clientA, serviceId, Instant.parse("2026-08-08T10:00:00Z"), Instant.parse("2026-08-08T10:30:00Z"));
        createScheduledAppointment(
                org, clientB, serviceId, Instant.parse("2026-08-01T15:00:00Z"), Instant.parse("2026-08-01T15:30:00Z"));

        mockMvc.perform(get("/api/v1/appointments?clientId=" + clientA).session(org.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startAt").value("2026-08-08T10:00:00Z"))
                .andExpect(jsonPath("$[1].startAt").value("2026-08-01T10:00:00Z"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/appointments?clientId=" + clientB).session(org.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listByClientDoesNotLeakAppointmentsFromAnotherOrganization() throws Exception {
        AuthenticatedSession orgA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession orgB = loginAsNewOwner("dona-b@exemplo.test");
        UUID clientA = createClient(orgA.organizationId(), "Cliente A");
        UUID serviceA = createService(orgA.organizationId(), "Corte", 30);
        createScheduledAppointment(
                orgA, clientA, serviceA, Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z"));

        mockMvc.perform(get("/api/v1/appointments?clientId=" + clientA).session(orgB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsOverlappingAppointmentAtApplicationLevel() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
    void deniesCreationWithinAnotherAppointmentsBufferButAllowsRightAfterIt() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createServiceWithBuffer(org.organizationId(), "Limpeza de pele", 30, 15);

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated());

        // 10:30-11:00 começaria exatamente quando o primeiro termina, mas o intervalo de
        // 15 minutos do serviço só libera o horário às 10:45.
        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:30:00Z"),
                                Instant.parse("2026-08-01T11:00:00Z")))))
                .andExpect(status().isConflict());

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:45:00Z"),
                                Instant.parse("2026-08-01T11:15:00Z")))))
                .andExpect(status().isCreated());
    }

    @Test
    void rescheduleRespectsTheAppointmentsOwnBuffer() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createServiceWithBuffer(org.organizationId(), "Limpeza de pele", 30, 15);

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

        // Remarcar o segundo para começar exatamente às 10:30 (fim do primeiro) ainda cai
        // dentro do intervalo de 15 minutos dele, que só libera às 10:45.
        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + secondId + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T10:30:00Z"), Instant.parse("2026-08-01T11:00:00Z")))))
                .andExpect(status().isConflict());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + secondId + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T10:45:00Z"), Instant.parse("2026-08-01T11:15:00Z")))))
                .andExpect(status().isOk());
    }

    @Test
    void reschedulesAppointmentAndRecordsAuditWithPreviousAndNewTimes() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + UUID.randomUUID() + "/reschedule", org)
                        .content(objectMapper.writeValueAsString(new RescheduleAppointmentRequest(
                                Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void editsAppointmentClientAndServiceRecomputingEndAtAndRecordsAudit() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientA = createClient(org.organizationId(), "Fulana de Tal");
        UUID clientB = createClient(org.organizationId(), "Beltrana da Silva");
        UUID serviceA = createService(org.organizationId(), "Corte", 30);
        UUID serviceB = createService(org.organizationId(), "Coloração", 90);

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientA,
                                serviceA,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID appointmentId = UUID.fromString(objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + appointmentId + "/edit", org)
                        .content(objectMapper.writeValueAsString(new EditAppointmentRequest(clientB, serviceB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Beltrana da Silva"))
                .andExpect(jsonPath("$.serviceName").value("Coloração"))
                .andExpect(jsonPath("$.startAt").value("2026-08-01T10:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-08-01T11:30:00Z"));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENT_EDITED' "
                        + "AND metadata->>'previousClientId' = '" + clientA + "' "
                        + "AND metadata->>'newClientId' = '" + clientB + "'",
                Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void rejectsEditToAnOverlappingSlot() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);
        UUID longerServiceId = createService(org.organizationId(), "Coloração", 90);

        mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T11:00:00Z"),
                                Instant.parse("2026-08-01T11:30:00Z")))))
                .andExpect(status().isCreated());

        MvcResult secondResult = mockMvc.perform(authenticatedPost("/api/v1/appointments", org)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z")))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID secondId = UUID.fromString(objectMapper
                .readTree(secondResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        // Trocar o segundo agendamento (10:00) para o serviço de 90 minutos faz o fim
        // avançar para 11:30, invadindo o primeiro agendamento (11:00-11:30).
        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + secondId + "/edit", org)
                        .content(objectMapper.writeValueAsString(new EditAppointmentRequest(clientId, longerServiceId))))
                .andExpect(status().isConflict());
    }

    @Test
    void editOfUnknownAppointmentReturnsNotFound() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/appointments/" + UUID.randomUUID() + "/edit", org)
                        .content(objectMapper.writeValueAsString(new EditAppointmentRequest(clientId, serviceId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelsAppointmentRecordsReasonAndFreesUpTheSlotForANewAppointment() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
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
        AuthenticatedSession orgA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession orgB = loginAsNewOwner("dona-b@exemplo.test");
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

    @Test
    void createsWeeklyRecurringSeriesWithSharedSeriesIdAndSevenDayInterval() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        MvcResult result = mockMvc.perform(authenticatedPost("/api/v1/appointments/recurring", org)
                        .content(objectMapper.writeValueAsString(new CreateRecurringAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z"),
                                RecurrenceFrequency.WEEKLY,
                                3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startAt").value("2026-08-01T10:00:00Z"))
                .andExpect(jsonPath("$[1].startAt").value("2026-08-08T10:00:00Z"))
                .andExpect(jsonPath("$[2].startAt").value("2026-08-15T10:00:00Z"))
                .andReturn();

        String firstSeriesId = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get(0)
                .get("seriesId")
                .asText();
        assertThat(firstSeriesId).isNotBlank();

        Long sameSeriesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE series_id = ?", Long.class, UUID.fromString(firstSeriesId));
        assertThat(sameSeriesCount).isEqualTo(3L);

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'APPOINTMENT_CREATED'", Long.class);
        assertThat(auditCount).isEqualTo(3L);
    }

    @Test
    void createsBiweeklyRecurringSeriesWithFourteenDayInterval() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/appointments/recurring", org)
                        .content(objectMapper.writeValueAsString(new CreateRecurringAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z"),
                                RecurrenceFrequency.BIWEEKLY,
                                2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startAt").value("2026-08-01T10:00:00Z"))
                .andExpect(jsonPath("$[1].startAt").value("2026-08-15T10:00:00Z"));
    }

    @Test
    void rejectsRecurringSeriesWhenAnOccurrenceConflictsAndCreatesNothingAtomically() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        // Ocupa o horário da terceira ocorrência (2026-08-15) com outro agendamento.
        createScheduledAppointment(
                org, clientId, serviceId, Instant.parse("2026-08-15T10:00:00Z"), Instant.parse("2026-08-15T10:30:00Z"));

        mockMvc.perform(authenticatedPost("/api/v1/appointments/recurring", org)
                        .content(objectMapper.writeValueAsString(new CreateRecurringAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z"),
                                RecurrenceFrequency.WEEKLY,
                                4))))
                .andExpect(status().isConflict());

        Long totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments", Long.class);
        assertThat(totalCount).isEqualTo(1L);
    }

    @Test
    void rejectsRecurringSeriesWithOccurrenceCountOutsideAllowedBounds() throws Exception {
        AuthenticatedSession org = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(org.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(org.organizationId(), "Corte", 30);

        mockMvc.perform(authenticatedPost("/api/v1/appointments/recurring", org)
                        .content(objectMapper.writeValueAsString(new CreateRecurringAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z"),
                                RecurrenceFrequency.WEEKLY,
                                1))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authenticatedPost("/api/v1/appointments/recurring", org)
                        .content(objectMapper.writeValueAsString(new CreateRecurringAppointmentRequest(
                                clientId,
                                serviceId,
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T10:30:00Z"),
                                RecurrenceFrequency.WEEKLY,
                                53))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesAppointmentCreationAndListingToSupport() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(owner.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(owner.organizationId(), "Corte", 30);
        AuthenticatedSession support = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SUPPORT", "suporte@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/appointments", support)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clientId, serviceId, Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:30:00Z")))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/appointments").session(support.session())).andExpect(status().isForbidden());
    }

    private UUID createScheduledAppointment(
            AuthenticatedSession org, UUID clientId, UUID serviceId, Instant startAt, Instant endAt) throws Exception {
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

    private UUID createServiceWithBuffer(UUID organizationId, String name, int durationMinutes, int bufferMinutes) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO services (id, organization_id, name, duration_minutes, buffer_minutes) VALUES (?, ?, ?, ?, ?)",
                id, organizationId, name, durationMinutes, bufferMinutes);
        return id;
    }

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
        return createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, email);
    }
}
