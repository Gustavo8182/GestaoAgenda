package br.com.agendaplatform.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ServiceControllerTest {

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
        jdbcTemplate.update("DELETE FROM services");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/services")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCreationWithBlankNameOrInvalidDuration() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsRawJsonPayloadWithOnlyTheRequiredFields() throws Exception {
        // Regressão: enviar apenas os campos obrigatórios via JSON puro (sem passar pelo
        // construtor Java, que sempre preenche os 5 campos do record antes de serializar)
        // já quebrou com "Cannot map `null` into type `boolean`" quando requiresConfirmation
        // era um boolean primitivo em vez de Boolean.
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content("{\"name\":\"Escova\",\"durationMinutes\":45}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiresConfirmation").value(false))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createsServiceScopedToCurrentOrganizationAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Limpeza de pele", 60))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Limpeza de pele"))
                .andExpect(jsonPath("$.durationMinutes").value(60));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'SERVICE_CREATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/catalog/services").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Limpeza de pele"));
    }

    @Test
    void rejectsInvalidColorFormat() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("Corte", 30, "azul", null, false, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsServiceWithColorConfirmationAndAutoIncrementedOrder() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("Corte", 30, "#3B82F6", null, true, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("#3B82F6"))
                .andExpect(jsonPath("$.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.displayOrder").value(0));

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("Escova", 45, null, null, false, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    void createsServiceWithBufferMinutesDefaultingToZeroWhenOmitted() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bufferMinutes").value(0));

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("Limpeza de pele", 30, null, null, false, 15))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bufferMinutes").value(15));
    }

    @Test
    void rejectsNegativeBufferMinutes() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("Corte", 30, null, null, false, -5))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersServicesByDisplayOrderThenName() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("Zebra", 30, null, 0, false, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("Alfa", 30, null, 5, false, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/catalog/services").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Zebra"))
                .andExpect(jsonPath("$[1].name").value("Alfa"));
    }

    @Test
    void editsServiceFieldsAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/edit", auth)
                        .content(objectMapper.writeValueAsString(
                                new EditServiceRequest("Corte premium", 45, "#112233", 3, true, 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Corte premium"))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.color").value("#112233"))
                .andExpect(jsonPath("$.displayOrder").value(3))
                .andExpect(jsonPath("$.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.bufferMinutes").value(10));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'SERVICE_EDITED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void editReturnsNotFoundForServiceFromAnotherOrganization() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", ownerA)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/edit", ownerB)
                        .content(objectMapper.writeValueAsString(
                                new EditServiceRequest("Corte", 30, null, 0, false, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deniesServiceEditingToSecretary() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession secretary = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SECRETARY", "secretaria@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", owner)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/edit", secretary)
                        .content(objectMapper.writeValueAsString(
                                new EditServiceRequest("Corte", 30, null, 0, false, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsEditWithInvalidFields() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/edit", auth)
                        .content(objectMapper.writeValueAsString(
                                new EditServiceRequest("", -5, "not-a-color", 0, false, -1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivatesServiceAndRecordsAuditWhileKeepingItListed() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/deactivate", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'SERVICE_DEACTIVATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/catalog/services").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void deactivateReturnsNotFoundForServiceFromAnotherOrganization() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", ownerA)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/deactivate", ownerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void reactivatesServiceAndRecordsAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", auth)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/deactivate", auth))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/reactivate", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'SERVICE_REACTIVATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void reactivateReturnsNotFoundForServiceFromAnotherOrganization() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", ownerA)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/reactivate", ownerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void deniesServiceReactivationToSecretary() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession secretary = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SECRETARY", "secretaria@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", owner)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();
        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/deactivate", owner))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/reactivate", secretary))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotLeakServicesBetweenOrganizations() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", ownerA)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("Serviço da organização A", 30))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/catalog/services").session(ownerB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deniesServiceCreationAndDeactivationToSecretary() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession secretary = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SECRETARY", "secretaria@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services", secretary)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isForbidden());

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/catalog/services", owner)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("Corte", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/catalog/services/" + serviceId + "/deactivate", secretary))
                .andExpect(status().isForbidden());

        // Leitura continua liberada para a secretária.
        mockMvc.perform(get("/api/v1/catalog/services").session(secretary.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deniesServiceListingToSupport() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession support = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SUPPORT", "suporte@exemplo.test");

        mockMvc.perform(get("/api/v1/catalog/services").session(support.session()))
                .andExpect(status().isForbidden());
    }

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
        return createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, email);
    }
}
