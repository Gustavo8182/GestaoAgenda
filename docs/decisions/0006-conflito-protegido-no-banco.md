# ADR 0006 — Conflito protegido no PostgreSQL

## Status

Implementado na etapa 5 da Feature 000 (agendamento).

## Decisão

Validar conflito na aplicação para boa mensagem e usar constraint de exclusão no PostgreSQL como proteção final contra concorrência.

## Implementação

`V005__appointments.sql` cria `CONSTRAINT appointments_no_overlap EXCLUDE USING gist (organization_id WITH =, tstzrange(start_at, end_at) WITH &&)`, usando a extensão `btree_gist` (habilitada desde a `V001`). A aplicação (`AppointmentScheduler`) verifica sobreposição antes de gravar, para retornar 409 com mensagem clara; se ainda assim duas requisições concorrentes passarem pela checagem simultaneamente, a constraint do banco rejeita a segunda com `DataIntegrityViolationException`, também mapeada para 409. Testado com Postgres real e duas threads concorrentes (`AppointmentOverlapConstraintTest`): exatamente uma inserção sobreposta é aceita.
