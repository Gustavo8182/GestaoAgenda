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
- [x] 2. Contexto da organização — resolução da organização/papel da usuária autenticada, exposta em `/login` e `/me`.
- [x] 3. Serviço — cadastro mínimo (nome + duração), escopado por organização, com auditoria.
- [x] 4. Cliente — cadastro mínimo (nome + telefone normalizado), aviso de duplicidade não bloqueante.
- [x] 5. Agendamento e constraint de sobreposição — validado na aplicação e no PostgreSQL (`EXCLUDE`), com teste de concorrência real.
- [x] 6. Lista no frontend — feita junto de cada etapa (Serviços, Clientes, Agenda já têm formulário e lista reais).
- [x] 7. Auditoria — feita junto de cada etapa (`SERVICE_CREATED`, `CLIENT_CREATED`, `APPOINTMENT_CREATED`).
- [ ] 8. E2E.

Detalhes técnicos da etapa 1: usuária autenticada por e-mail/senha (`br.com.agendaplatform.identity`), senha com `DelegatingPasswordEncoder` (bcrypt), sessão persistida em `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` (Flyway `V002`), CSRF por cookie (`XSRF-TOKEN`/`X-XSRF-TOKEN`), conta `DISABLED`/`INVITED` não autentica. Seed de desenvolvimento (`db/dev-seed`, perfil `local` apenas) cria `dona@exemplo.test` / `TrocarSenha123!` — ver `docs/operations/local-development.md`.

Detalhes técnicos da etapa 2: módulo `organizations` mapeia `organizations`/`organization_members` (JPA); `CurrentOrganizationProvider` (bean por requisição, `br.com.agendaplatform.organizations`) resolve a organização ativa a partir do `userId` do principal autenticado — nunca de dado enviado pelo navegador. O `userId` chega ao principal via `IdentityUserDetails`, que implementa o contrato compartilhado `shared.security.AuthenticatedPrincipal` (evita o módulo `organizations` acessar internals de `identity`). Usuária sem vínculo ativo com nenhuma organização recebe 403 mesmo com credenciais corretas. `/api/v1/auth/login` e `/api/v1/auth/me` agora retornam `{ user, organization }`. Seed de dev (`V901`) cria a organização de demonstração com a usuária como `OWNER`.

Detalhes técnicos da etapa 3: módulo `catalog` (`services`, Flyway `V003`) com `POST/GET /api/v1/catalog/services` (nome + duração em minutos, sem cor/ordem/confirmação — fora do escopo desta fatia). O `organizationId` sempre vem do `CurrentOrganizationProvider`, nunca do corpo da requisição. Criado o módulo `auditing` com o contrato `AuditRecorder` (grava quem/quando/o quê em `audit_logs`; metadata JSON ainda não mapeada, para não arriscar incompatibilidade Hibernate 7 + Jackson 3 não testada); toda criação de serviço grava `SERVICE_CREATED`. Extraído `shared.security.CurrentActorProvider`, reaproveitado pelo `CurrentOrganizationProvider` e pela auditoria. Painel ganhou formulário e lista reais em "Serviços".

Ressalva de ambiente local: os seeds de dev usam versões Flyway altas (`V900+`) para nunca colidir com migrações reais; se o volume local do Postgres já tiver esses seeds aplicados, uma migração nova numerada abaixo de 900 (como `V003`) fica "fora de ordem" até rodar `docker compose down -v`. Detalhes em `docs/operations/local-development.md`.

Detalhes técnicos da etapa 4: módulo `clients` (`clients`, Flyway `V004`) com `POST/GET /api/v1/clients` (nome + telefone; sem origem, observações, telefone alternativo, restrição de contato ou busca — fora do escopo desta fatia). `PhoneNormalizer` remove tudo que não é dígito e só descarta o código do país "55" quando sobrarem 12–13 dígitos (nunca confunde com um DDD "55" legítimo, que tem 10–11 dígitos). Duplicidade é verificada por telefone normalizado *dentro da mesma organização* e apenas gera aviso (`possibleDuplicate`) — nunca bloqueia o cadastro, conforme regra de que duas pessoas podem compartilhar o mesmo número. Toda criação registra `CLIENT_CREATED` via `AuditRecorder`. Painel ganhou formulário e lista reais em "Clientes", com banner de aviso quando a API sinaliza duplicidade.

Detalhes técnicos da etapa 5: módulo `scheduling` (`appointments`, Flyway `V005`) com `POST/GET /api/v1/appointments`. Uma única agenda por organização (ADR 0002): a constraint `EXCLUDE USING gist (organization_id WITH =, tstzrange(start_at, end_at) WITH &&)` (usa `btree_gist`, já habilitada na V001) impede qualquer sobreposição na mesma organização, independente de cliente ou serviço; validado com teste de concorrência real (duas threads competindo para inserir o mesmo horário — exatamente uma é aceita). A aplicação também verifica sobreposição antes de gravar, para dar uma mensagem clara (409) em vez de deixar o erro cru do banco vazar; o `DataIntegrityViolationException` da constraint continua mapeado para 409 como rede de segurança final contra corrida. Novos contratos públicos mínimos `catalog.ServiceLookup` e `clients.ClientLookup` permitem ao `scheduling` confirmar que cliente/serviço pertencem à organização atual e obter o nome para exibição, sem acessar internals desses módulos. Painel ganhou a página "Agenda" com lista simples (sem calendário visual, fora do escopo desta fatia) — a duração do agendamento é calculada a partir do serviço escolhido.

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
