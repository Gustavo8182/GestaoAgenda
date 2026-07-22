package br.com.agendaplatform.scheduling.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Prova, com Postgres real e duas threads concorrentes, que a constraint de exclusão
 * (V005__appointments.sql) é a barreira final contra sobreposição — mesmo que duas
 * requisições passem simultaneamente pela validação da aplicação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class AppointmentOverlapConstraintTest {

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
        jdbcTemplate.update("DELETE FROM appointments");
        jdbcTemplate.update("DELETE FROM clients");
        jdbcTemplate.update("DELETE FROM services");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void exactlyOneOfTwoConcurrentOverlappingInsertsSucceeds() throws Exception {
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                organizationId, "Organização de teste", "organizacao-teste-" + organizationId);

        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO clients (id, organization_id, name, phone, phone_normalized) VALUES (?, ?, ?, ?, ?)",
                clientId, organizationId, "Fulana de Tal", "21999999999", "21999999999");

        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO services (id, organization_id, name, duration_minutes) VALUES (?, ?, ?, ?)",
                serviceId, organizationId, "Corte", 30);

        Timestamp start = Timestamp.from(Instant.parse("2026-08-01T10:00:00Z"));
        Timestamp end = Timestamp.from(Instant.parse("2026-08-01T11:00:00Z"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Boolean> insertTask = () -> {
            ready.countDown();
            go.await();
            try {
                jdbcTemplate.update(
                        "INSERT INTO appointments (organization_id, client_id, service_id, start_at, end_at) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        organizationId, clientId, serviceId, start, end);
                return true;
            } catch (DataAccessException exception) {
                return false;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(insertTask);
            Future<Boolean> second = executor.submit(insertTask);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            boolean firstSucceeded = first.get(10, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(10, TimeUnit.SECONDS);

            assertThat(firstSucceeded ^ secondSucceeded)
                    .as("exactamente uma das duas inserções sobrepostas deve ser aceita pelo banco")
                    .isTrue();
        } finally {
            executor.shutdown();
        }

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE organization_id = ?", Long.class, organizationId);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void cancellingAnAppointmentFreesUpTheSlotAtTheDatabaseLevel() {
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')",
                organizationId, "Organização de teste", "organizacao-teste-" + organizationId);

        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO clients (id, organization_id, name, phone, phone_normalized) VALUES (?, ?, ?, ?, ?)",
                clientId, organizationId, "Fulana de Tal", "21999999999", "21999999999");

        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO services (id, organization_id, name, duration_minutes) VALUES (?, ?, ?, ?)",
                serviceId, organizationId, "Corte", 30);

        UUID firstAppointmentId = UUID.randomUUID();
        Timestamp start = Timestamp.from(Instant.parse("2026-08-01T10:00:00Z"));
        Timestamp end = Timestamp.from(Instant.parse("2026-08-01T11:00:00Z"));
        jdbcTemplate.update(
                "INSERT INTO appointments (id, organization_id, client_id, service_id, start_at, end_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                firstAppointmentId, organizationId, clientId, serviceId, start, end);

        jdbcTemplate.update(
                "UPDATE appointments SET status = 'CANCELLED', cancellation_reason = ? WHERE id = ?",
                "Motivo de teste.", firstAppointmentId);

        jdbcTemplate.update(
                "INSERT INTO appointments (organization_id, client_id, service_id, start_at, end_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                organizationId, clientId, serviceId, start, end);

        Long scheduledCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE organization_id = ? AND status = 'SCHEDULED'",
                Long.class,
                organizationId);
        assertThat(scheduledCount).isEqualTo(1L);
    }
}
