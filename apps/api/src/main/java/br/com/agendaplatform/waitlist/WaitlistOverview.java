package br.com.agendaplatform.waitlist;

import java.util.List;
import java.util.UUID;

/**
 * Permite que outros módulos leiam todos os registros da lista de espera de uma organização
 * (ex.: exportação CSV), sem acessar as classes internas do módulo waitlist.
 */
public interface WaitlistOverview {

    List<WaitlistExportRow> findAll(UUID organizationId);
}
