package br.com.agendaplatform.scheduling;

import java.time.Instant;
import java.util.UUID;

/**
 * Permite que outros módulos criem um agendamento (ex.: ao converter um registro da lista de
 * espera) reaproveitando toda a validação de disponibilidade já existente, sem acessar as
 * classes internas do módulo scheduling.
 */
public interface AppointmentBooking {

    AppointmentSummary create(UUID clientId, UUID serviceId, Instant startAt, Instant endAt);
}
