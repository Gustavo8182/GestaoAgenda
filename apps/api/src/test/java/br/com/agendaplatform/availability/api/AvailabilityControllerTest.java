package br.com.agendaplatform.availability.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.addMemberAndLogin;
import static br.com.agendaplatform.support.IntegrationTestSupport.authenticatedPost;
import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;

import br.com.agendaplatform.support.IntegrationTestSupport.AuthenticatedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class AvailabilityControllerTest {

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
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/availability/business-hours")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBusinessHoursWithEndNotAfterStart() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPut("/api/v1/availability/business-hours", auth)
                        .content("[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00:00\",\"endTime\":\"09:00:00\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBusinessHoursWithDuplicateDay() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPut("/api/v1/availability/business-hours", auth)
                        .content("[" + "{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00:00\",\"endTime\":\"12:00:00\"},"
                                + "{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"14:00:00\",\"endTime\":\"18:00:00\"}" + "]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replacesBusinessHoursAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPut("/api/v1/availability/business-hours", auth)
                        .content("[" + "{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"},"
                                + "{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}" + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'BUSINESS_HOURS_UPDATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        // Uma segunda chamada substitui inteiramente a configuração anterior.
        mockMvc.perform(authenticatedPut("/api/v1/availability/business-hours", auth)
                        .content("[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"10:00:00\",\"endTime\":\"16:00:00\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"));

        mockMvc.perform(get("/api/v1/availability/business-hours").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsBlockWithBlankReason() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/availability/blocks", auth)
                        .content("{\"startAt\":\"2026-08-01T10:00:00Z\",\"endAt\":\"2026-08-01T11:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlockWithEndNotAfterStart() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/availability/blocks", auth)
                        .content("{\"startAt\":\"2026-08-01T10:00:00Z\",\"endAt\":\"2026-08-01T10:00:00Z\","
                                + "\"reason\":\"Motivo qualquer\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsListsAndRemovesBlockRecordingAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/availability/blocks", auth)
                        .content("{\"startAt\":\"2026-08-01T12:00:00Z\",\"endAt\":\"2026-08-01T13:00:00Z\","
                                + "\"reason\":\"Almoço\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("Almoço"))
                .andReturn();
        String blockId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        Long createdAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'BLOCK_CREATED'", Long.class);
        assertThat(createdAuditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/availability/blocks").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(authenticatedDelete("/api/v1/availability/blocks/" + blockId, auth))
                .andExpect(status().isNoContent());

        Long removedAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'BLOCK_REMOVED'", Long.class);
        assertThat(removedAuditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/availability/blocks").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removeReturnsNotFoundForBlockFromAnotherOrganization() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/availability/blocks", ownerA)
                        .content("{\"startAt\":\"2026-08-01T12:00:00Z\",\"endAt\":\"2026-08-01T13:00:00Z\","
                                + "\"reason\":\"Almoço\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String blockId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedDelete("/api/v1/availability/blocks/" + blockId, ownerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void doesNotLeakBusinessHoursOrBlocksBetweenOrganizations() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        mockMvc.perform(authenticatedPut("/api/v1/availability/business-hours", ownerA)
                        .content("[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}]"))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedPost("/api/v1/availability/blocks", ownerA)
                        .content("{\"startAt\":\"2026-08-01T12:00:00Z\",\"endAt\":\"2026-08-01T13:00:00Z\","
                                + "\"reason\":\"Almoço\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/availability/business-hours").session(ownerB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/availability/blocks").session(ownerB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deniesBusinessHoursChangeToSecretaryButAllowsBlockManagement() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession secretary = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SECRETARY", "secretaria@exemplo.test");

        mockMvc.perform(authenticatedPut("/api/v1/availability/business-hours", secretary)
                        .content("[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}]"))
                .andExpect(status().isForbidden());

        // Leitura do horário de funcionamento continua liberada para a secretária.
        mockMvc.perform(get("/api/v1/availability/business-hours").session(secretary.session()))
                .andExpect(status().isOk());

        // Bloqueios pontuais continuam liberados para a secretária.
        mockMvc.perform(authenticatedPost("/api/v1/availability/blocks", secretary)
                        .content("{\"startAt\":\"2026-08-01T12:00:00Z\",\"endAt\":\"2026-08-01T13:00:00Z\","
                                + "\"reason\":\"Almoço\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void deniesBusinessHoursAndBlockAccessToSupport() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession support = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SUPPORT", "suporte@exemplo.test");

        mockMvc.perform(get("/api/v1/availability/business-hours").session(support.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(authenticatedPut("/api/v1/availability/business-hours", support)
                        .content("[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/availability/blocks").session(support.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(authenticatedPost("/api/v1/availability/blocks", support)
                        .content("{\"startAt\":\"2026-08-01T12:00:00Z\",\"endAt\":\"2026-08-01T13:00:00Z\","
                                + "\"reason\":\"Almoço\"}"))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder authenticatedPut(String url, AuthenticatedSession auth) {
        return put(url)
                .session(auth.session())
                .cookie(auth.csrfCookie())
                .header("X-XSRF-TOKEN", auth.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder authenticatedDelete(String url, AuthenticatedSession auth) {
        return delete(url)
                .session(auth.session())
                .cookie(auth.csrfCookie())
                .header("X-XSRF-TOKEN", auth.csrfCookie().getValue());
    }

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
        return createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, email);
    }
}
