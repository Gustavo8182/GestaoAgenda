# Registro inicial de riscos

| Risco | Impacto | Mitigação inicial |
|---|---|---|
| Vazamento entre organizações | Crítico | Contexto de organização, repositories seguros, testes e futura RLS |
| Sobreposição concorrente | Alto | Validação na aplicação e constraint de exclusão |
| Campo administrativo virar prontuário | Alto | Escopo, labels, limites e revisão de dados |
| Projeto crescer antes da validação | Alto | Fatias verticais e exclusões explícitas |
| Dependências sem lockfile inicial | Médio | Resolvido: `package-lock.json` gerado e versionado na Fase 1.1; CI usa `npm ci` |
| Backup não restaurável | Alto | Procedimento e teste de restauração antes da produção |
| Suporte expor dados | Alto | Acesso temporário, mínimo e auditado |
