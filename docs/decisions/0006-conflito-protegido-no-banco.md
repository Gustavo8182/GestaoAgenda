# ADR 0006 — Conflito protegido no PostgreSQL

## Status

Aceito para a fatia de agendamentos.

## Decisão

Validar conflito na aplicação para boa mensagem e usar constraint de exclusão no PostgreSQL como proteção final contra concorrência.

## Observação

A migração será criada junto da primeira implementação real de agendamentos, com teste concorrente em PostgreSQL real.
