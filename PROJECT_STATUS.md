# Status

## Fundação inicial

- [x] estrutura do repositório;
- [x] API Spring Boot;
- [x] módulos delimitados;
- [x] migração inicial;
- [x] painel Angular navegável;
- [x] Compose local;
- [x] CI inicial;
- [x] documentação para agentes;
- [x] primeira fatia especificada;
- [x] lockfile npm gerado em ambiente com internet;
- [x] build completo validado com JDK 25 e Node compatível;
- [x] repositório Git criado;
- [ ] cliente-piloto validado;
- [ ] primeira fatia implementada (em andamento — sessão e autenticação concluídas).

## Feature 000 — Primeira fatia vertical

Progresso por etapa (ver `docs/features/000-first-vertical-slice.md` e `docs/architecture/security.md`):

- [x] 1. Sessão e autenticação — login por e-mail/senha, sessão via Spring Session JDBC, CSRF ativo, logout, `/api/v1/auth/me`, guard e página de login no painel.
- [ ] 2. Contexto da organização.
- [ ] 3. Serviço.
- [ ] 4. Cliente.
- [ ] 5. Agendamento e constraint de sobreposição.
- [ ] 6. Lista no frontend.
- [ ] 7. Auditoria.
- [ ] 8. E2E.

Detalhes técnicos da etapa 1: usuária autenticada por e-mail/senha (`br.com.agendaplatform.identity`), senha com `DelegatingPasswordEncoder` (bcrypt), sessão persistida em `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` (Flyway `V002`), CSRF por cookie (`XSRF-TOKEN`/`X-XSRF-TOKEN`), conta `DISABLED`/`INVITED` não autentica. Seed de desenvolvimento (`db/dev-seed`, perfil `local` apenas) cria `dona@exemplo.test` / `TrocarSenha123!` — ver `docs/operations/local-development.md`.

## Fase 1.1 — Validação e fechamento da fundação técnica

Concluída em 2026-07-21 com ressalvas. Ver `docs/qa/foundation-validation.md` para o detalhamento completo.

Resumo das correções aplicadas nesta fase (nenhuma é funcionalidade de negócio):

- Maven Wrapper gerado (`apps/api/mvnw`, `mvnw.cmd`, `.mvn/wrapper/`); scripts, CI e docs migrados de `mvn` para `./mvnw`.
- `pom.xml`: artifactIds do Testcontainers corrigidos (`testcontainers-junit-jupiter`, `testcontainers-postgresql`) — o build nem compilava antes.
- `pom.xml`: dependência do Flyway trocada de `flyway-core` para `spring-boot-starter-flyway` — no Spring Boot 4.1 o autoconfigure do Flyway saiu do jar monolítico e precisa do novo starter; sem isso o Flyway nunca rodava e o app subia silenciosamente sem nenhuma tabela.
- `compose.yaml`: volume do Postgres corrigido para `/var/lib/postgresql` (Postgres 18+ mudou a convenção; o mount antigo em `/var/lib/postgresql/data` deixava o container preso em `unhealthy`).
- `apps/admin/tsconfig.json`: removidas opções depreciadas (`baseUrl`, `downlevelIteration`) que quebravam a compilação com o TypeScript instalado.
- `apps/admin/package.json`: `temporal-polyfill` atualizado para `^1.0.1` para satisfazer o peer dependency do FullCalendar 7 (`npm install` falhava com `ERESOLVE`).
- `apps/admin/package-lock.json` gerado e versionado; CI e `apps/admin/Dockerfile` passaram a usar `npm ci`.
- Repositório Git inicializado nesta fase.

Ressalvas que continuam abertas: ambiente local sem `JAVA_HOME` de sistema em JDK 25 (contorno documentado), ausência de script `npm run lint`, 4 vulnerabilidades npm em tooling de desenvolvimento (não no bundle de produção), Dockerfiles não construídos nesta rodada. Detalhes em `docs/qa/foundation-validation.md`.
