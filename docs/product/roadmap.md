# Roadmap orientado a fatias verticais

## Fundação

- repositório;
- CI;
- módulos;
- banco local;
- segurança fechada;
- documentação.

## Fatia 1 — fluxo mínimo

- autenticação da proprietária;
- organização;
- serviço mínimo;
- cliente mínimo;
- criação de agendamento;
- visualização em lista;
- prevenção de sobreposição;
- auditoria.

## Fatia 2 — operação diária

- agenda dia e semana;
- status;
- remarcação;
- cancelamento e motivo;
- bloqueios;
- dashboard diário.

## Fatia 3 — recorrência e histórico

- recorrência semanal e quinzenal;
- conflitos da série;
- histórico do cliente;
- histórico de alterações.

## Fatia 4 — lista de espera

- cadastro;
- compatibilidade com vaga;
- conversão;
- indicadores básicos.

## Fatia 5 — relacionamento básico

- contato pendente;
- próxima ação;
- origem;
- conversão em cliente e agendamento.

## Fatia 6 — exportação e encerramento

- CSV;
- auditoria;
- processo de encerramento e retenção.

## Backlog (pós-MVP, aguardando validação com cliente-piloto)

Itens conscientemente adiados em alinhamento de escopo de 2026-07-23 — não são exclusões
definitivas (ver `docs/product/functional-scope.md`), só ainda não são prioridade real. Retomar
quando um cliente-piloto validar a necessidade, não por iniciativa própria do agente.

- **Bloqueio recorrente semanal** (ex.: "toda segunda de manhã", "todo domingo"): série com data
  inicial, dia da semana, horário inicial/final, ativo/inativo e data final opcional (repetir
  enquanto ativo, sem exigir quantidade fixa de ocorrências — diferente do agendamento recorrente,
  que usa contagem). Bloqueio pontual e de múltiplos dias (início/fim independentes, já
  suportado hoje) **não precisam ser redesenhados** — só a recorrência de verdade é o gap.
  - Para a primeira implementação, série inteira + ativar/desativar já é suficiente; **não**
    precisa suportar editar/cancelar uma ocorrência isolada, ou "esta e as próximas" — mas a
    arquitetura escolhida não deve impossibilitar adicionar essas exceções depois (mesmo cuidado
    já tomado com `series_id` nos agendamentos recorrentes).
  - Fora do escopo desta fatia futura: recorrência anual específica para feriados (feriados
    continuam sendo bloqueios pontuais/intervalos normais), gerador de vários bloqueios pontuais
    de uma vez, padrões mensais ou regras avançadas de calendário.
- **Acesso técnico de SUPPORT** (modelo alinhado em `docs/architecture/security.md`, seção
  "Autorização"): acesso excepcional, somente leitura, escopado a uma organização, temporário e
  auditado — sem sistema de tickets/aprovação formal nesta fase.
- **Troca de papel de uma usuária existente** (`SECRETARY` ↔ `OWNER`, só por `OWNER`, com proteção
  contra organização ficar sem nenhuma `OWNER` ativa e auditoria obrigatória) — ver
  `docs/architecture/security.md`.
- **Exclusão/anonimização automática por retenção** (hoje a regra provisória de 30 dias após
  encerramento em `docs/operations/production-readiness.md` é só uma política de produto, sem
  automação) e **fluxo completo de anonimização LGPD** — dependem de revisão jurídica prévia.
