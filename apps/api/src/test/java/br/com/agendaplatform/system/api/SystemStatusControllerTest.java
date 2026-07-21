package br.com.agendaplatform.system.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SystemStatusControllerTest {

    @Test
    void returnsPublicApiStatus() {
        var response = new SystemStatusController().status();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().service()).isEqualTo("agenda-api");
        assertThat(response.getBody().status()).isEqualTo("UP");
    }
}
