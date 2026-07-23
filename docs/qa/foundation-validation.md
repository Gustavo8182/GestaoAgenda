# Validação da fundação técnica (Fase 1.1)

Data: 2026-07-21.

## Objetivo

Confirmar que o starter compila, testa e roda de ponta a ponta com PostgreSQL real, sem implementar funcionalidades de negócio.

## Ambiente usado na validação

- Windows 11, PowerShell/Git Bash.
- JDK Temurin 25.0.3 instalado, mas `JAVA_HOME` do sistema apontava para o JBR do Android Studio (Java 21). Os comandos Maven desta validação foram executados com `JAVA_HOME` sobrescrito para o Temurin 25 na própria chamada. **Ressalva**: quem for reproduzir localmente no Windows precisa garantir que `JAVA_HOME` (ou o `java` resolvido no `PATH` do Maven) aponte para um JDK 25, senão `mvn`/`mvnw` falha ao compilar com `release 25`.
- Node.js 24.18.0, npm 11.16.0.
- Docker Desktop 29.5.3 / Compose v5.1.4 (precisou ser iniciado manualmente; o serviço não estava rodando no começo da sessão).
- Maven de sistema 3.9.16 (usado só para gerar o wrapper); depois disso todos os comandos passaram a usar `./mvnw`.

## Problemas reais encontrados e corrigidos

1. **Maven Wrapper ausente.** Não havia `mvnw`, `mvnw.cmd` nem `.mvn/wrapper/`. Gerado com `mvn wrapper:wrapper -Dmaven=3.9.11` a partir de `apps/api`. Scripts (`scripts/check.sh`, `scripts/check.ps1`, `Makefile`), CI (`.github/workflows/ci.yml`) e documentação (`AGENTS.md`, `README.md`, `apps/api/README.md`, `docs/operations/local-development.md`) foram atualizados para usar `./mvnw`/`./mvnw.cmd` em vez do `mvn` do sistema.

2. **`pom.xml` não compilava: artifactIds do Testcontainers desatualizados.** O projeto declarava `org.testcontainers:junit-jupiter` e `org.testcontainers:postgresql`, artifactIds do Testcontainers 1.x. O BOM gerenciado pelo Spring Boot 4.1 já é o Testcontainers 2.0.5, que renomeou esses módulos para `testcontainers-junit-jupiter` e `testcontainers-postgresql` (prefixo `testcontainers-` obrigatório). Isso quebrava a resolução do POM antes de qualquer build. Corrigido em `apps/api/pom.xml`.

3. **Flyway nunca era executado, mas a aplicação subia "com sucesso" mesmo assim.** Em Spring Boot 4.1 o autoconfigure do Flyway foi extraído para o módulo `spring-boot-flyway`, só trazido pelo novo starter `org.springframework.boot:spring-boot-starter-flyway`. Declarar apenas `org.flywaydb:flyway-core` (como no Boot 3.x) não é mais suficiente — a classe de autoconfiguração nem aparece no relatório de condições. Como não existe nenhuma entidade JPA ainda, `ddl-auto=validate` não tinha nada para validar e o app subia normalmente sem nenhuma tabela criada — um risco silencioso sério assim que a primeira entidade for adicionada. Corrigido substituindo a dependência `flyway-core` por `spring-boot-starter-flyway` em `apps/api/pom.xml` (mantendo `flyway-database-postgresql` explícito). Validado rodando a API contra PostgreSQL real: `flyway_schema_history` e as tabelas de `V001__identity_and_audit_foundation.sql` foram criadas corretamente.

4. **`compose.yaml` incompatível com a imagem `postgres:18.4-alpine`.** O volume estava montado em `/var/lib/postgresql/data`, convenção anterior ao Postgres 18. A partir da versão 18 a imagem oficial espera um único mount em `/var/lib/postgresql` (o `PGDATA` passa a viver numa subpasta versionada, ex. `18/docker`, para suportar `pg_ctlcluster`/`pg_upgrade --link`). Com o mount antigo o container entra em loop de erro e nunca fica saudável. Corrigido o `volumes:` do serviço `postgres` em `compose.yaml`.

5. **`tsconfig.json` do Angular não compilava com o TypeScript instalado (`~6.0.3`).** As opções `baseUrl` e `downlevelIteration` são tratadas como erro (`TS5101`) pelo compilador atual quando `ignoreDeprecations` não está setado. Nenhum arquivo do projeto usa import baseado em `baseUrl`, e `downlevelIteration` não tem efeito com `target: ES2022`. Ambas as opções foram removidas de `apps/admin/tsconfig.json` em vez de suprimir o erro, por serem scaffolding órfão sem uso real.

6. **Conflito de peer dependency no frontend: `temporal-polyfill@^0.3.0` vs. `fullcalendar@7.0.1` (`peer temporal-polyfill@^1.0.1`).** `npm install` falhava com `ERESOLVE`. Atualizado `apps/admin/package.json` para `temporal-polyfill@^1.0.1` (última versão publicada, compatível com o peer exigido). Não é um upgrade "porque existe versão nova" — é a versão mínima necessária para o `npm install` funcionar sem `--legacy-peer-deps`.

7. **Lockfile do frontend ausente.** Gerado `apps/admin/package-lock.json` via `npm install` (ambiente desta sessão tinha acesso à internet). CI (`.github/workflows/ci.yml`) e `apps/admin/Dockerfile` foram atualizados de `npm install` para `npm ci`, conforme a própria observação já registrada no README anterior.

## Validações executadas (com resultado real)

| Comando | Resultado |
|---|---|
| `docker compose config` | OK — configuração válida. |
| `docker compose up -d postgres` (após corrigir o volume) | OK — healthcheck `healthy` em poucos segundos. |
| `apps/api: ./mvnw -B wrapper:wrapper -Dmaven=3.9.11` | OK — wrapper gerado. |
| `apps/api: ./mvnw -B clean verify` | OK — `BUILD SUCCESS`, 2 testes (`ArchitectureTest`, `SystemStatusControllerTest`), 0 falhas. |
| `apps/api: ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` contra Postgres real | OK — `Started AgendaApiApplication`, Flyway aplicou `V001` (`flyway_schema_history` com 1 linha `success=t`), `/actuator/health` → `UP`, `/api/v1/system/status` → `UP`, endpoint protegido sem sessão → `403` (nega por padrão, conforme esperado). |
| `apps/admin: npm install` (antes da correção do `temporal-polyfill`) | Falhou com `ERESOLVE`. Corrigido, ver item 6 acima. |
| `apps/admin: npm ci` (após lockfile gerado) | OK — reinstala a partir do lockfile sem alterar versões. |
| `apps/admin: npm run test:ci` | OK — 1 arquivo de teste, 1 teste, passou. |
| `apps/admin: npm run build` | OK — build de produção gerado em `dist/admin`, dentro dos orçamentos de tamanho configurados. |
| `apps/admin: npm audit` | 4 vulnerabilidades (1 baixa, 3 moderadas), todas em dependências de desenvolvimento do Angular CLI/Vite (não vão para o bundle de produção). Corrigi-las via `npm audit fix --force` rebaixaria o Angular CLI para a série 21, o que contradiz a stack fixada (Angular 22) — não aplicado. Registrado como ressalva. |

## Ressalvas restantes

- **Docker Desktop não iniciava sozinho** (serviço parado no começo da sessão); precisou ser iniciado manualmente. Ambientes de CI (GitHub Actions com `ubuntu-latest`) já trazem Docker ativo, então isso só afeta reprodução local nesta máquina específica. Quem quiser que inicie junto do Windows: Docker Desktop → Settings → General → "Start Docker Desktop when you log in".
- **4 vulnerabilidades npm** em dependências de desenvolvimento (Angular CLI → MCP SDK → `@hono/node-server`; `esbuild` do dev server do Vite). Risco limitado a tooling local de build/test, não ao bundle de produção. Reconfirmado em 2026-07-22: `npm audit fix` (sem `--force`) não altera o lockfile — a correção de todas as 4 continua exigindo `--force` (rebaixaria o Angular CLI para a série 21). Decisão de não corrigir mantida.

## Ressalvas fechadas (revalidado em 2026-07-22)

- **Script `npm run lint` ausente**: adicionado via `ng add @angular-eslint/schematics` — regras recomendadas do ESLint + `typescript-eslint` (recommended + stylistic) + `angular-eslint` (incluindo regras de acessibilidade nos templates HTML). Rodado contra todo o código já existente: só 1 erro em todo o painel (`ReadonlyArray<T>` em vez de `readonly T[]` em `settings-page.component.ts`), corrigido automaticamente via `ng lint --fix`. Adicionado como etapa no job `frontend` do CI, antes dos testes.
- **`JAVA_HOME`**: a causa raiz não era falta de instalação — o JDK Temurin 25 já estava instalado e o `JAVA_HOME` de **máquina** (`Machine`) já apontava corretamente para ele. O problema é que existe também um `JAVA_HOME` de **usuário** (`User`) apontando para o JBR do Android Studio (Java 21), e no Windows a variável de usuário tem precedência sobre a de máquina — por isso `./mvnw` resolvia Java 21 mesmo com o `PATH` e a variável de máquina corretos. Correção (o usuário precisa rodar, é uma alteração de ambiente do Windows): ver `docs/operations/local-development.md`, seção "Ambiente Windows".
- **Dockerfiles**: ambos construídos e validados nesta rodada. `apps/api/Dockerfile` tinha um bug real — o estágio de build rodava `mvn verify` (não só `package`), o que executa a suíte de testes de integração com Testcontainers; como esse estágio não tem acesso ao socket do Docker do host (sem Docker-in-Docker configurado), o build travava/falhava tentando abrir um container Postgres de dentro de si mesmo. Corrigido para `mvn -DskipTests package` (os testes já rodam no job `backend` do CI e em `./mvnw verify` local; a imagem só precisa empacotar o artefato já validado). Depois da correção: a imagem builda em ~27s e, testada de ponta a ponta contra um Postgres real (fora do Testcontainers, um container comum), sobe, aplica as 13 migrações (incluindo os seeds de dev) e responde `UP` em `/actuator/health`. `apps/admin/Dockerfile` builda normalmente; ao rodar isoladamente (fora do `docker compose`), o nginx falha ao iniciar com `host not found in upstream "api"` — **isso é esperado, não é um bug**: o `infra/nginx/default.conf` faz proxy para um serviço chamado `api` na mesma rede Docker, que só existe quando este container roda junto de um serviço `api` (ainda não há um `compose.yaml` de stack completa versionado; hoje `compose.yaml` só sobe `postgres`/`mailpit` para desenvolvimento local, com a API rodando fora do Docker via `./mvnw spring-boot:run`). Registrado aqui para quem for escrever esse compose de deploy futuro.
- **E2E não integrado ao CI**: adicionado o job `e2e` em `.github/workflows/ci.yml` — sobe um serviço Postgres, empacota e inicia a API com o perfil `local`, sobe o painel via `ng serve`, roda o Playwright contra os dois. Não foi possível validar a execução real deste job a partir daqui (exigiria efetivamente disparar o GitHub Actions); a primeira execução no CI depois do push deve ser conferida manualmente.
- Nenhuma entidade JPA existia no momento desta validação original, então `ddl-auto=validate` não tinha sido exercitado contra um schema real. Isso está superado: desde a Feature 000, toda a suíte de testes de integração (Testcontainers) roda com `ddl-auto=validate` contra um schema Postgres real migrado pelo Flyway, em todo `./mvnw verify` — validado continuamente a cada rodada desde então.

## Fora do escopo desta fase

Nenhuma funcionalidade de negócio (clientes, serviços, agenda, autenticação real) foi iniciada. Apenas a fundação técnica foi corrigida.
