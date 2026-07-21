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

- **Não existe script `npm run lint`** em `apps/admin/package.json`. Não foi adicionado ESLint/tooling de lint nesta fase por não haver necessidade comprovada ainda e para não introduzir infraestrutura antecipada; fica como decisão pendente para quando o primeiro conjunto de regras de código for definido.
- **`JAVA_HOME` do sistema não aponta para o JDK 25** nesta máquina (aponta para o JBR do Android Studio, Java 21). Isso não é um problema do repositório, mas quem reproduzir localmente nesta mesma máquina precisa ajustar `JAVA_HOME` antes de rodar `./mvnw`/`./mvnw.cmd`, senão a compilação falha ao pedir `release 25` a partir de um JDK mais antigo.
- **Docker Desktop não iniciava sozinho** (serviço parado no começo da sessão); precisou ser iniciado manualmente. Ambientes de CI (GitHub Actions com `ubuntu-latest`) já trazem Docker ativo, então isso só afeta reprodução local nesta máquina específica.
- **4 vulnerabilidades npm** em dependências de desenvolvimento (Angular CLI → MCP SDK → `@hono/node-server`; `esbuild` do dev server do Vite). Risco limitado a tooling local de build/test, não ao bundle de produção. Não corrigido para não forçar downgrade do Angular CLI.
- **Dockerfiles não foram construídos nesta validação** (`apps/api/Dockerfile`, `apps/admin/Dockerfile`). O build local via wrapper/npm foi validado; o build de imagem Docker completo fica como verificação futura antes de qualquer deploy.
- Nenhuma entidade JPA existe ainda, então `ddl-auto=validate` não foi exercitado de fato contra um schema com entidades mapeadas — isso só será validado quando a primeira fatia vertical (`docs/features/000-first-vertical-slice.md`) adicionar entidades reais.

## Fora do escopo desta fase

Nenhuma funcionalidade de negócio (clientes, serviços, agenda, autenticação real) foi iniciada. Apenas a fundação técnica foi corrigida.
