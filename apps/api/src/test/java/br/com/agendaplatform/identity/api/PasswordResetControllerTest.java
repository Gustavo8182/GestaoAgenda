package br.com.agendaplatform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class PasswordResetControllerTest {

    private static final String RAW_PASSWORD = "SenhaForte123!";
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
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void requestReturnsGenericMessageAndDoesNotSendEmailForUnknownAddress() throws Exception {
        mockMvc.perform(authenticatedRequest("/api/v1/auth/password-reset/request")
                        .content(objectMapper.writeValueAsString(
                                new RequestPasswordResetRequest("desconhecida@exemplo.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Se este e-mail estiver cadastrado, você vai receber instruções para redefinir a senha."));

        verifyNoInteractions(mailSender);
    }

    @Test
    void requestSendsEmailWithResetLinkForKnownActiveUser() throws Exception {
        createUser("dona@exemplo.test", RAW_PASSWORD, "ACTIVE");

        mockMvc.perform(authenticatedRequest("/api/v1/auth/password-reset/request")
                        .content(objectMapper.writeValueAsString(new RequestPasswordResetRequest("dona@exemplo.test"))))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("dona@exemplo.test");
        assertThat(sent.getText()).contains("/redefinir-senha?token=");

        Long tokenCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Long.class);
        assertThat(tokenCount).isEqualTo(1L);
    }

    @Test
    void confirmChangesPasswordAndAllowsLoginWithNewPassword() throws Exception {
        UUID userId = createUser("dona@exemplo.test", RAW_PASSWORD, "ACTIVE");
        addOrganizationMembership(userId, "OWNER", "Clínica de teste");
        String token = requestAndCaptureToken("dona@exemplo.test");

        mockMvc.perform(authenticatedRequest("/api/v1/auth/password-reset/confirm")
                        .content(objectMapper.writeValueAsString(
                                new ConfirmPasswordResetRequest(token, "NovaSenhaForte456!"))))
                .andExpect(status().isOk());

        Cookie csrfCookie = fetchCsrfCookie();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("dona@exemplo.test", "NovaSenhaForte456!"))))
                .andExpect(status().isOk());

        Cookie oldPasswordCsrf = fetchCsrfCookie();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(oldPasswordCsrf)
                        .header("X-XSRF-TOKEN", oldPasswordCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("dona@exemplo.test", RAW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmFailsWithUnknownToken() throws Exception {
        mockMvc.perform(authenticatedRequest("/api/v1/auth/password-reset/confirm")
                        .content(objectMapper.writeValueAsString(
                                new ConfirmPasswordResetRequest("token-invalido", "NovaSenhaForte456!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmFailsWhenTokenAlreadyUsed() throws Exception {
        createUser("dona@exemplo.test", RAW_PASSWORD, "ACTIVE");
        String token = requestAndCaptureToken("dona@exemplo.test");

        mockMvc.perform(authenticatedRequest("/api/v1/auth/password-reset/confirm")
                        .content(objectMapper.writeValueAsString(
                                new ConfirmPasswordResetRequest(token, "NovaSenhaForte456!"))))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedRequest("/api/v1/auth/password-reset/confirm")
                        .content(objectMapper.writeValueAsString(
                                new ConfirmPasswordResetRequest(token, "OutraSenhaForte789!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmRejectsShortPassword() throws Exception {
        createUser("dona@exemplo.test", RAW_PASSWORD, "ACTIVE");
        String token = requestAndCaptureToken("dona@exemplo.test");

        mockMvc.perform(authenticatedRequest("/api/v1/auth/password-reset/confirm")
                        .content(objectMapper.writeValueAsString(new ConfirmPasswordResetRequest(token, "curta"))))
                .andExpect(status().isBadRequest());
    }

    private String requestAndCaptureToken(String email) throws Exception {
        mockMvc.perform(authenticatedRequest("/api/v1/auth/password-reset/request")
                        .content(objectMapper.writeValueAsString(new RequestPasswordResetRequest(email))))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        String body = captor.getValue().getText();
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private MockHttpServletRequestBuilder authenticatedRequest(String url) throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        return post(url)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private UUID createUser(String email, String rawPassword, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, status) VALUES (?, ?, ?, ?, ?)",
                id, email, passwordEncoder.encode(rawPassword), "Usuária de teste", status);
        return id;
    }

    private void addOrganizationMembership(UUID userId, String role, String organizationName) {
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                organizationId, organizationName, "organizacao-teste-" + organizationId);
        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, role, status) VALUES (?, ?, ?, 'ACTIVE')",
                organizationId, userId, role);
    }
}
