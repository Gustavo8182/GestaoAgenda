# ADR 0006 — Conflito protegido no PostgreSQL

## Status

Implementado na etapa 5 da Feature 000 (agendamento); ajustado na etapa de remarcação/cancelamento, novamente no ciclo completo de status (pós Feature 000) e novamente na rodada de intervalo opcional no serviço.

## Decisão

Validar conflito na aplicação para boa mensagem e usar constraint de exclusão no PostgreSQL como proteção final contra concorrência.

## Implementação

`V005__appointments.sql` cria `CONSTRAINT appointments_no_overlap EXCLUDE USING gist (organization_id WITH =, tstzrange(start_at, end_at) WITH &&)`, usando a extensão `btree_gist` (habilitada desde a `V001`). A aplicação (`AppointmentScheduler`) verifica sobreposição antes de gravar, para retornar 409 com mensagem clara; se ainda assim duas requisições concorrentes passarem pela checagem simultaneamente, a constraint do banco rejeita a segunda com `DataIntegrityViolationException`, também mapeada para 409. Testado com Postgres real e duas threads concorrentes (`AppointmentOverlapConstraintTest`): exatamente uma inserção sobreposta é aceita.

`V008__appointments_status_and_reschedule.sql` recriou a constraint como **exclusão parcial** (`... WHERE (status = 'SCHEDULED')`): sem o predicado, um agendamento cancelado continuaria "ocupando" o horário para sempre, impedindo um novo agendamento no mesmo slot. A checagem de sobreposição na aplicação (`existsOverlappingExcluding`) também filtra por `status = SCHEDULED` e exclui o próprio registro (necessário para a remarcação revalidar contra os *outros* agendamentos, não contra si mesma). Testado com Postgres real (`AppointmentOverlapConstraintTest.cancellingAnAppointmentFreesUpTheSlotAtTheDatabaseLevel`).

`V010__appointments_full_status_lifecycle.sql` generalizou o predicado de `WHERE (status = 'SCHEDULED')` para `WHERE (status NOT IN ('CANCELLED', 'NO_SHOW'))`, quando o ciclo completo de status (confirmado, chegou, em atendimento, realizado, falta) foi implementado: todos esses status intermediários também precisam continuar ocupando o horário — só cancelamento e falta o liberam. A checagem na aplicação (`existsOverlappingExcluding`) foi ajustada da mesma forma.

`V017__service_buffer_minutes.sql` estendeu a constraint para considerar um intervalo opcional (`buffer_minutes`) após o fim do agendamento: `tstzrange(start_at, add_minutes_to_timestamp(end_at, buffer_minutes))` em vez de só `tstzrange(start_at, end_at)`. O valor é copiado de `services.buffer_minutes` para `appointments.buffer_minutes` no momento da criação (snapshot na própria linha) — diferente do conflito agendamento-vs-bloqueio (que permanece só na aplicação, por exigir junção entre tabelas), aqui o dado já está na mesma linha, então a proteção real de concorrência no banco continua possível. Expressões de índice GiST exigem que a função usada seja `IMMUTABLE`; `timestamptz + interval` é marcada `STABLE` pelo Postgres (mesmo sendo determinística para um intervalo só em minutos), então a migração cria `add_minutes_to_timestamp(ts, minutes)`, que soma segundos via `epoch` (sempre UTC, sem ambiguidade de fuso) e por isso é genuinamente `IMMUTABLE`. Testado com Postgres real, tanto via API (`AppointmentControllerTest`) quanto diretamente por JDBC cru contra a constraint (`AppointmentOverlapConstraintTest.constraintRejectsInsertWithinAnotherAppointmentsBufferAtTheDatabaseLevel`), provando que a proteção funciona mesmo contornando a aplicação.
