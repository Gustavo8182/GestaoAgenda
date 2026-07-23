package br.com.agendaplatform.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;

import br.com.agendaplatform.support.IntegrationTestSupport.AuthenticatedSession;
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

/**
 * Cobre o achado de auditoria de segurança "Actuator exposto a qualquer autenticada": só
 * {@code /actuator/health} deve responder — as demais superfícies do Actuator não devem estar
 * publicamente acessíveis, nem para uma usuária de negócio autenticada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ActuatorSecurityTest {

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
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void healthCheckIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void metricsAndInfoAreNotExposedWithoutSession() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void metricsAndInfoAreNotExposedEvenWithAnAuthenticatedSession() throws Exception {
        AuthenticatedSession auth = createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, "dona@exemplo.test");

        mockMvc.perform(get("/actuator/metrics").session(auth.session())).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/info").session(auth.session())).andExpect(status().isNotFound());
    }
}
