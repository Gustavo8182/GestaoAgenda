package br.com.agendaplatform.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.agendaplatform.identity.api.LoginRequest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(DashboardControllerTest.FixedClockConfig.class)
class DashboardControllerTest {

    private static final String RAW_PASSWORD = "SenhaForte123!";

    // Quarta-feira 2026-08-05, 10:00 em America/Sao_Paulo (organização default).
    private static final Instant NOW = Instant.parse("2026-08-05T13:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

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
        jdbcTemplate.update("DELETE FROM blocks");
        jdbcTemplate.update("DELETE FROM business_hours");
        jdbcTemplate.update("DELETE FROM appointments");
        jdbcTemplate.update("DELETE FROM clients");
        jdbcTemplate.update("DELETE FROM services");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsEmptySummaryWhenNothingIsScheduled() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(get("/api/v1/dashboard").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayAppointments.length()").value(0))
                .andExpect(jsonPath("$.nextAppointment").doesNotExist())
                .andExpect(jsonPath("$.todayBlocks.length()").value(0))
                .andExpect(jsonPath("$.week.scheduledCount").value(0))
                .andExpect(jsonPath("$.week.cancelledCount").value(0));
    }

    @Test
    void assemblesTodayNextUpcomingAndWeekSummaryAcrossScopes() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");
        UUID clientId = createClient(auth.organizationId(), "Fulana de Tal");
        UUID serviceId = createService(auth.organizationId(), "Corte", 30);

        // Hoje, no passado (já passou, não deve ser "próximo atendimento").
        createAppointment(
                auth.organizationId(),
                clientId,
                serviceId,
                Instant.parse("2026-08-05T11:00:00Z"),
                Instant.parse("2026-08-05T11:30:00Z"),
                "SCHEDULED",
                null);
        // Hoje, no futuro (deve ser o "próximo atendimento").
        createAppointment(
                auth.organizationId(),
                clientId,
                serviceId,
                Instant.parse("2026-08-05T15:00:00Z"),
                Instant.parse("2026-08-05T15:30:00Z"),
                "SCHEDULED",
                null);
        // Hoje, cancelado.
        createAppointment(
                auth.organizationId(),
                clientId,
                serviceId,
                Instant.parse("2026-08-05T20:00:00Z"),
                Instant.parse("2026-08-05T20:30:00Z"),
                "CANCELLED",
                "Motivo de teste");
        // Amanhã (mesma semana, não deve aparecer em "hoje").
        createAppointment(
                auth.organizationId(),
                clientId,
                serviceId,
                Instant.parse("2026-08-06T15:00:00Z"),
                Instant.parse("2026-08-06T15:30:00Z"),
                "SCHEDULED",
                null);
        // Semana seguinte (não deve contar no resumo semanal).
        createAppointment(
                auth.organizationId(),
                clientId,
                serviceId,
                Instant.parse("2026-08-10T15:00:00Z"),
                Instant.parse("2026-08-10T15:30:00Z"),
                "SCHEDULED",
                null);

        createBlock(
                auth.organizationId(),
                Instant.parse("2026-08-05T18:00:00Z"),
                Instant.parse("2026-08-05T19:00:00Z"),
                "Bloqueio de teste");
        createBlock(
                auth.organizationId(),
                Instant.parse("2026-08-06T18:00:00Z"),
                Instant.parse("2026-08-06T19:00:00Z"),
                "Bloqueio de amanhã");

        mockMvc.perform(get("/api/v1/dashboard").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayAppointments.length()").value(3))
                .andExpect(jsonPath("$.nextAppointment.startAt").value("2026-08-05T15:00:00Z"))
                .andExpect(jsonPath("$.todayBlocks.length()").value(1))
                .andExpect(jsonPath("$.todayBlocks[0].reason").value("Bloqueio de teste"))
                .andExpect(jsonPath("$.week.scheduledCount").value(3))
                .andExpect(jsonPath("$.week.cancelledCount").value(1));
    }

    @Test
    void doesNotLeakDashboardDataBetweenOrganizations() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        UUID clientA = createClient(ownerA.organizationId(), "Cliente A");
        UUID serviceA = createService(ownerA.organizationId(), "Corte", 30);
        createAppointment(
                ownerA.organizationId(),
                clientA,
                serviceA,
                Instant.parse("2026-08-05T15:00:00Z"),
                Instant.parse("2026-08-05T15:30:00Z"),
                "SCHEDULED",
                null);

        mockMvc.perform(get("/api/v1/dashboard").session(ownerB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayAppointments.length()").value(0))
                .andExpect(jsonPath("$.nextAppointment").doesNotExist());
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

    private void createAppointment(
            UUID organizationId,
            UUID clientId,
            UUID serviceId,
            Instant startAt,
            Instant endAt,
            String status,
            String cancellationReason) {
        jdbcTemplate.update(
                "INSERT INTO appointments (id, organization_id, client_id, service_id, start_at, end_at, status, "
                        + "cancellation_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                organizationId,
                clientId,
                serviceId,
                Timestamp.from(startAt),
                Timestamp.from(endAt),
                status,
                cancellationReason);
    }

    private void createBlock(UUID organizationId, Instant startAt, Instant endAt, String reason) {
        jdbcTemplate.update(
                "INSERT INTO blocks (id, organization_id, start_at, end_at, reason) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), organizationId, Timestamp.from(startAt), Timestamp.from(endAt), reason);
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
        return new AuthenticatedSession(organizationId, session, csrfCookie);
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private record AuthenticatedSession(UUID organizationId, MockHttpSession session, Cookie csrfCookie) {
    }
}
