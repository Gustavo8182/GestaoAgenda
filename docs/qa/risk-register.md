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
| Token de reset de senha antigo continuar aceito após novo pedido | Médio | Resolvido (auditoria de 2026-07-23): `PasswordResetService.requestReset()` agora invalida qualquer token pendente da mesma usuária (`PasswordResetToken.invalidate()`) antes de emitir um novo — um link antigo nunca mais funciona depois que um novo é pedido. |
| Duas organizações ativas para a mesma usuária derrubam o login com 500 | Baixa | Resolvido (auditoria de 2026-07-23): constraint única parcial no banco (`V018__one_active_membership_per_user.sql`) impede o estado de existir; `SecurityCurrentOrganizationProvider` também trata `IncorrectResultSizeDataAccessException` como defesa em profundidade, respondendo 403 em vez de 500. |
