# ADR 0001 — Monólito modular

## Status

Aceito.

## Decisão

Usar uma aplicação Spring Boot organizada por módulos de domínio, verificada pelo Spring Modulith.

## Consequências

- implantação e transações simples;
- módulos precisam respeitar limites;
- nenhum microserviço será criado sem problema operacional comprovado.
