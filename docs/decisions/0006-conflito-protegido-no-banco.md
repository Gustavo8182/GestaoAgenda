# ADR 0006 — Conflito protegido no PostgreSQL

## Status

Implementado na etapa 5 da Feature 000 (agendamento); ajustado na etapa de remarcação/cancelamento (pós Feature 000).

## Decisão

Validar conflito na aplicação para boa mensagem e usar constraint de exclusão no PostgreSQL como proteção final contra concorrência.

## Implementação

`V005__appointments.sql` cria `CONSTRAINT appointments_no_overlap EXCLUDE USING gist (organization_id WITH =, tstzrange(start_at, end_at) WITH &&)`, usando a extensão `btree_gist` (habilitada desde a `V001`). A aplicação (`AppointmentScheduler`) verifica sobreposição antes de gravar, para retornar 409 com mensagem clara; se ainda assim duas requisições concorrentes passarem pela checagem simultaneamente, a constraint do banco rejeita a segunda com `DataIntegrityViolationException`, também mapeada para 409. Testado com Postgres real e duas threads concorrentes (`AppointmentOverlapConstraintTest`): exatamente uma inserção sobreposta é aceita.

`V008__appointments_status_and_reschedule.sql` recriou a constraint como **exclusão parcial** (`... WHERE (status = 'SCHEDULED')`): sem o predicado, um agendamento cancelado continuaria "ocupando" o horário para sempre, impedindo um novo agendamento no mesmo slot. A checagem de sobreposição na aplicação (`existsOverlappingExcluding`) também filtra por `status = SCHEDULED` e exclui o próprio registro (necessário para a remarcação revalidar contra os *outros* agendamentos, não contra si mesma). Testado com Postgres real (`AppointmentOverlapConstraintTest.cancellingAnAppointmentFreesUpTheSlotAtTheDatabaseLevel`).
