package br.com.agendaplatform.clients.api;

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
class ClientControllerTest {

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
        jdbcTemplate.update("DELETE FROM clients");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCreationWithBlankNameOrInvalidPhone() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest("", "abc"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPhoneThatDoesNotHaveEnoughDigits() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest("Fulana", "999"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsClientAndRecordsAuditWithoutDuplicateWarning() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "(21) 99999-9999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.name").value("Fulana de Tal"))
                .andExpect(jsonPath("$.possibleDuplicate").value(false));

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'CLIENT_CREATED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/clients").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Fulana de Tal"));
    }

    @Test
    void flagsPossibleDuplicateForDifferentlyFormattedSamePhoneInSameOrganization() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "(21) 99999-9999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(false));

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana da Silva", "+55 21 99999-9999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(true));

        mockMvc.perform(get("/api/v1/clients").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsInvalidAlternatePhone() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "21999999999", "123", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsClientWithAlternatePhoneOriginAndNotes() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest(
                                "Fulana de Tal",
                                "(21) 99999-9999",
                                "(21) 98888-7777",
                                "Indicação de amiga",
                                "Prefere horários pela manhã"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.alternatePhone").value("(21) 98888-7777"))
                .andExpect(jsonPath("$.client.origin").value("Indicação de amiga"))
                .andExpect(jsonPath("$.client.notes").value("Prefere horários pela manhã"));
    }

    @Test
    void flagsPossibleDuplicateWhenAlternatePhoneMatchesAnExistingPrimaryPhone() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "21999999999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(false));

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest(
                                "Fulana da Silva", "21988887777", "21999999999", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(true));
    }

    @Test
    void searchFindsClientsByPartialNameOrPhoneDigits() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "(21) 99999-1234"))))
                .andExpect(status().isCreated());
        mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Beltrana Souza", "(21) 97777-5678"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/clients").session(auth.session()).param("query", "beltra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Beltrana Souza"));

        mockMvc.perform(get("/api/v1/clients").session(auth.session()).param("query", "1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Fulana de Tal"));

        mockMvc.perform(get("/api/v1/clients").session(auth.session()).param("query", "não existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void doesNotFlagDuplicateOrLeakClientsBetweenOrganizations() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", ownerA)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana da Organização A", "21999999999"))))
                .andExpect(status().isCreated());

        mockMvc.perform(authenticatedPost("/api/v1/clients", ownerB)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana da Organização B", "21999999999"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(false));

        mockMvc.perform(get("/api/v1/clients").session(ownerB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Fulana da Organização B"));
    }

    @Test
    void deniesClientCreationAndListingToSupport() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession support = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SUPPORT", "suporte@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/clients", support)
                        .content(objectMapper.writeValueAsString(new CreateClientRequest("Fulana de Tal", "21999999999"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/clients").session(support.session())).andExpect(status().isForbidden());
    }

    @Test
    void restrictsAndLiftsContactRestrictionRecordingAudit() throws Exception {
        AuthenticatedSession auth = loginAsNewOwner("dona@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/clients", auth)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "21999999999"))))
                .andExpect(status().isCreated())
                .andReturn();
        String clientId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("client")
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/clients/" + clientId + "/restrict-contact", auth)
                        .content("{\"reason\":\"Pediu para não ser mais contatada.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactRestricted").value(true))
                .andExpect(jsonPath("$.contactRestrictionReason").value("Pediu para não ser mais contatada."));

        Long restrictedAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'CLIENT_CONTACT_RESTRICTED'", Long.class);
        assertThat(restrictedAuditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/clients").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactRestricted").value(true));

        mockMvc.perform(authenticatedPost("/api/v1/clients/" + clientId + "/lift-contact-restriction", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactRestricted").value(false))
                .andExpect(jsonPath("$.contactRestrictionReason").doesNotExist());

        Long liftedAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'CLIENT_CONTACT_RESTRICTION_LIFTED'", Long.class);
        assertThat(liftedAuditCount).isEqualTo(1L);
    }

    @Test
    void deniesContactRestrictionActionsToSupport() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession support = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SUPPORT", "suporte@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/clients", owner)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana de Tal", "21999999999"))))
                .andExpect(status().isCreated())
                .andReturn();
        String clientId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("client")
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/clients/" + clientId + "/restrict-contact", support)
                        .content("{\"reason\":\"Motivo qualquer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void restrictContactReturnsNotFoundForClientFromAnotherOrganization() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");

        MvcResult createResult = mockMvc.perform(authenticatedPost("/api/v1/clients", ownerA)
                        .content(objectMapper.writeValueAsString(
                                new CreateClientRequest("Fulana da Organização A", "21999999999"))))
                .andExpect(status().isCreated())
                .andReturn();
        String clientId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("client")
                .get("id")
                .asText();

        mockMvc.perform(authenticatedPost("/api/v1/clients/" + clientId + "/restrict-contact", ownerB)
                        .content("{\"reason\":\"Motivo qualquer\"}"))
                .andExpect(status().isNotFound());
    }

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
        return createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, email);
    }
}
