package br.com.agendaplatform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static br.com.agendaplatform.support.IntegrationTestSupport.authenticatedPost;
import static br.com.agendaplatform.support.IntegrationTestSupport.createOrganizationWithOwner;
import static br.com.agendaplatform.support.IntegrationTestSupport.fetchCsrfCookie;

import br.com.agendaplatform.support.IntegrationTestSupport.AuthenticatedSession;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class InvitationControllerTest {

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
        jdbcTemplate.update("DELETE FROM user_invitation_tokens");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void acceptFailsWithUnknownToken() throws Exception {
        mockMvc.perform(acceptInvitationRequest()
                        .content("{\"token\":\"token-invalido\",\"newPassword\":\"SenhaNova123!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptFailsWhenTokenAlreadyUsed() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        String token = inviteAndCaptureToken(owner, "secretaria@exemplo.test");

        mockMvc.perform(acceptInvitationRequest()
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"SenhaNova123!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(acceptInvitationRequest()
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"OutraSenha456!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptRejectsShortPassword() throws Exception {
        AuthenticatedSession owner = loginAsNewOwner("dona@exemplo.test");
        String token = inviteAndCaptureToken(owner, "secretaria@exemplo.test");

        mockMvc.perform(acceptInvitationRequest()
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"curta\"}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder acceptInvitationRequest() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie(mockMvc);
        return post("/api/v1/auth/invitations/accept")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String inviteAndCaptureToken(AuthenticatedSession owner, String email) throws Exception {
        mockMvc.perform(authenticatedPost("/api/v1/organizations/members", owner)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"Secretária Nova\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        Matcher matcher = TOKEN_PATTERN.matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private AuthenticatedSession loginAsNewOwner(String email) throws Exception {
        return createOrganizationWithOwner(mockMvc, objectMapper, jdbcTemplate, passwordEncoder, email);
    }
}
