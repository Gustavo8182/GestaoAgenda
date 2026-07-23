package br.com.agendaplatform.organizations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prova, com Postgres real, que a constraint única parcial (V018) impede que a mesma usuária
 * fique com vínculo ATIVO em mais de uma organização ao mesmo tempo — mesmo contornando a regra
 * de convite (que já bloqueia isso pelo caminho normal da aplicação) via inserção direta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class OrganizationMemberConstraintTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM organization_members");
        jdbcTemplate.update("DELETE FROM organizations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void constraintRejectsASecondActiveMembershipForTheSameUserAtTheDatabaseLevel() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, status) VALUES (?, ?, ?, ?, ?)",
                userId, "dona@exemplo.test", "hash", "Usuária de teste", "ACTIVE");

        UUID firstOrganizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                firstOrganizationId, "Organização A", "organizacao-a-" + firstOrganizationId);
        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, role, status) VALUES (?, ?, ?, 'ACTIVE')",
                firstOrganizationId, userId, "OWNER");

        UUID secondOrganizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                secondOrganizationId, "Organização B", "organizacao-b-" + secondOrganizationId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO organization_members (organization_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, 'ACTIVE')",
                        secondOrganizationId, userId, "OWNER"))
                .isInstanceOf(DataAccessException.class);

        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_members WHERE user_id = ? AND status = 'ACTIVE'",
                Long.class,
                userId);
        assertThat(activeCount).isEqualTo(1L);
    }

    @Test
    void allowsASecondMembershipWhenTheFirstIsNotActive() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, status) VALUES (?, ?, ?, ?, ?)",
                userId, "dona@exemplo.test", "hash", "Usuária de teste", "ACTIVE");

        UUID firstOrganizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                firstOrganizationId, "Organização A", "organizacao-a-" + firstOrganizationId);
        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, role, status) VALUES (?, ?, ?, 'DISABLED')",
                firstOrganizationId, userId, "OWNER");

        UUID secondOrganizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                secondOrganizationId, "Organização B", "organizacao-b-" + secondOrganizationId);

        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, role, status) VALUES (?, ?, ?, 'ACTIVE')",
                secondOrganizationId, userId, "OWNER");

        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_members WHERE user_id = ?", Long.class, userId);
        assertThat(totalCount).isEqualTo(2L);
    }
}
