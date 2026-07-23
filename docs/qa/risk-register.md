# Registro inicial de riscos

| Risco | Impacto | Mitigação inicial |
|---|---|---|
| Vazamento entre organizações | Crítico | Contexto de organização, repositories seguros, testes e futura RLS |
| Sobreposição concorrente (agendamento x agendamento) | Alto | Validação na aplicação e constraint `EXCLUDE` no PostgreSQL (ADR 0006) |
| Sobreposição concorrente (agendamento x bloqueio) | Médio | Só validado na aplicação — sem constraint no banco (exclusão cruzando duas tabelas não é direta no Postgres); aceito por ora dado o baixo volume de concorrência esperado (uma secretária por organização). Revisar se o padrão de uso mudar. Ver `PROJECT_STATUS.md`, seção "Horários de funcionamento e bloqueios". |
| Campo administrativo virar prontuário | Alto | Escopo, labels, limites e revisão de dados |
| Projeto crescer antes da validação | Alto | Fatias verticais e exclusões explícitas |
| Dependências sem lockfile inicial | Médio | Resolvido: `package-lock.json` gerado e versionado na Fase 1.1; CI usa `npm ci` |
| Backup não restaurável | Alto | Procedimento e teste de restauração antes da produção |
| Suporte expor dados | Alto | Acesso temporário, mínimo e auditado |
| Revogação de sessão não se propaga entre instâncias | Médio | `SessionRegistry` é em memória (não compartilhado); só afeta cenário de múltiplas instâncias da API, hoje rodando em instância única. Ativar Spring Session JDBC resolveria — ver `docs/architecture/security.md`, seção "Limitação conhecida". |
