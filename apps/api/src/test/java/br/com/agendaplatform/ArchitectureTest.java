package br.com.agendaplatform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(AgendaApiApplication.class).verify();
    }
}
