package br.com.agendaplatform.membership.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.addMemberAndLogin;
import static br.com.agendaplatform.support.IntegrationTestSupport.authenticatedPost;
import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;
import static br.com.agendaplatform.support.IntegrationTestSupport.fetchCsrfCookie;

import br.com.agendaplatform.support.IntegrationTestSupport.AuthenticatedSession;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class MembershipControllerTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([\\w-]+)");

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

    @MockitoBean
    private JavaMailSender mailSender;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM user_invitation_tokens");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void deniesAccessWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void deniesInviteListDisableAndReactivateToSecretary() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession secretary = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SECRETARY", "secretaria@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/organizations/members", secretary)
                        .content(objectMapper.writeValueAsString(new InviteMemberRequest("nova@exemplo.test", "Nova Secretária"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/organizations/members").session(secretary.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(authenticatedPost("/api/v1/organizations/members/" + java.util.UUID.randomUUID() + "/disable", secretary))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitesNewMemberSendsEmailAndListsAsInvited() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/organizations/members", owner)
                        .content(objectMapper.writeValueAsString(
                                new InviteMemberRequest("secretaria@exemplo.test", "Secretária Nova"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("secretaria@exemplo.test"))
                .andExpect(jsonPath("$.displayName").value("Secretária Nova"))
                .andExpect(jsonPath("$.role").value("SECRETARY"))
                .andExpect(jsonPath("$.status").value("INVITED"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("secretaria@exemplo.test");
        assertThat(captor.getValue().getText()).contains("/aceitar-convite?token=");

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'MEMBER_INVITED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/organizations/members").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.email=='secretaria@exemplo.test')].status").value("INVITED"));
    }

    @Test
    void rejectsInviteWithAlreadyRegisteredEmail() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/organizations/members", owner)
                        .content(objectMapper.writeValueAsString(new InviteMemberRequest("dona@exemplo.test", "Outra"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_registered"));
    }

    @Test
    void invitedMemberAcceptsInvitationAndBecomesActive() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/organizations/members", owner)
                        .content(objectMapper.writeValueAsString(
                                new InviteMemberRequest("secretaria@exemplo.test", "Secretária Nova"))))
                .andExpect(status().isCreated());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        Matcher matcher = TOKEN_PATTERN.matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        String token = matcher.group(1);

        jakarta.servlet.http.Cookie csrfCookie = fetchCsrfCookie(mockMvc);
        mockMvc.perform(post("/api/v1/auth/invitations/accept")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"SenhaNova123!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizations/members").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='secretaria@exemplo.test')].status").value("ACTIVE"));
    }

    @Test
    void ownerDisablesMemberRevokingActiveSessionAndReactivatesLater() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        AuthenticatedSession secretary = addMemberAndLogin(
                mockMvc, objectMapper, jdbcTemplate, passwordEncoder, owner.organizationId(), "SECRETARY", "secretaria@exemplo.test");

        mockMvc.perform(get("/api/v1/auth/me").session(secretary.session())).andExpect(status().isOk());

        String memberId = fetchMemberId(owner, "secretaria@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/organizations/members/" + memberId + "/disable", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/auth/me").session(secretary.session())).andExpect(status().isUnauthorized());

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'MEMBER_DISABLED'", Long.class);
        assertThat(auditCount).isEqualTo(1L);

        mockMvc.perform(authenticatedPost("/api/v1/organizations/members/" + memberId + "/reactivate", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ownerCannotDisableSelfOrAnotherOwner() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        String ownMemberId = fetchMemberId(owner, "dona@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/organizations/members/" + ownMemberId + "/disable", owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_membership_action"));
    }

    @Test
    void disablingReturnsNotFoundForMemberFromAnotherOrganization() throws Exception {
        AuthenticatedSession ownerA = loginAsNewOwner("dona-a@exemplo.test");
        AuthenticatedSession ownerB = loginAsNewOwner("dona-b@exemplo.test");
        String memberIdInOrgA = fetchMemberId(ownerA, "dona-a@exemplo.test");

        mockMvc.perform(authenticatedPost("/api/v1/organizations/members/" + memberIdInOrgA + "/disable", ownerB))
                .andExpect(status().isNotFound());
    }

    private String fetchMemberId(AuthenticatedSession owner, String email) throws Exception {
        var result = mockMvc.perform(get("/api/v1/organizations/members").session(owner.session()))
                .andExpect(status().isOk())
                .andReturn();
        var members = objectMapper.readTree(result.getResponse().getContentAsString());
        for (var member : members) {
            if (member.get("email").asText().equals(email)) {
                return member.get("id").asText();
            }
        }
        throw new IllegalStateException("Membro não encontrado na resposta: " + email);
    }

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
        return createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, email);
    }
}
