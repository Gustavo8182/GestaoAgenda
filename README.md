# Agenda Platform Starter

Base inicial para um SaaS multiempresa de agenda administrativa e relacionamento básico para pequenos negócios com atendimento por hora marcada.

> O nome comercial ainda não foi definido. O identificador técnico `agenda-platform` é provisório.

## Stack fixada

- Java 25 LTS
- Spring Boot 4.1
- Spring MVC, Security, Data JPA, Validation, Actuator e Flyway
- Spring Modulith 2.1 para verificação da arquitetura modular
- PostgreSQL 18
- Angular 22 com TypeScript estrito
- Angular Material/CDK previstos para a interface
- FullCalendar Standard previsto para a agenda
- Vitest no frontend
- JUnit, Spring Modulith Test e Testcontainers no backend
- Docker Compose para dependências locais
- GitHub Actions para integração contínua

## Estado desta base

A fundação técnica está fechada (ver `docs/qa/foundation-validation.md`) e a primeira fatia
vertical (Feature 000) está implementada de ponta a ponta — ver `PROJECT_STATUS.md` para o
detalhamento por etapa.

Já contém:

- monorepo organizado, módulos de domínio delimitados (Spring Modulith);
- autenticação por e-mail/senha, sessão persistida, CSRF ativo;
- contexto de organização resolvido pela sessão (nunca pelo corpo da requisição);
- cadastro de serviços e clientes (com normalização e aviso de duplicidade de telefone);
- criação de agendamentos com bloqueio de sobreposição (aplicação + constraint no PostgreSQL);
- auditoria de criação para serviço, cliente e agendamento;
- painel Angular funcional (login, serviços, clientes, agenda) — sem calendário visual completo;
- teste E2E (Playwright) do fluxo crítico;
- PostgreSQL e Mailpit no Docker Compose;
- documentação para agentes e decisões arquiteturais;
- pipeline inicial de CI.

Ainda não contém (fora do escopo da primeira fatia vertical):

- calendário visual (FullCalendar) integrado — a agenda hoje é uma lista simples;
- recorrência, remarcação e cancelamento de agendamentos;
- lista de espera;
- relacionamento (contatos que ainda não agendaram);
- relatórios e exportação;
- recuperação de senha e gestão de usuárias (convite, desativação);
- busca e edição de clientes/serviços, campos além de nome/telefone/duração;
- infraestrutura de produção.

## Pré-requisitos

- JDK 25 (o Maven Wrapper em `apps/api` dispensa uma instalação separada do Maven)
- Node.js 24.15+ (a base fixa 24.18.0) ou outra versão compatível com Angular 22
- npm 10+
- Docker com Compose

## Primeira execução

Copie as variáveis locais:

```bash
cp .env.example .env
```

No PowerShell:

```powershell
Copy-Item .env.example .env
```

Suba as dependências:

```bash
docker compose up -d
```

API:

```bash
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Painel:

```bash
cd apps/admin
npm install
npm start
```

Endereços locais:

- Painel: http://localhost:4200
- API: http://localhost:8080
- Status: http://localhost:8080/api/v1/system/status
- Health: http://localhost:8080/actuator/health
- Mailpit: http://localhost:8025

## Verificações

Linux/macOS:

```bash
./scripts/check.sh
```

Windows PowerShell:

```powershell
./scripts/check.ps1
```

Teste E2E do fluxo crítico (login, serviço, cliente, agendamento) — exige Postgres, API
e painel já rodando (ver `docs/operations/local-development.md`):

```bash
cd apps/admin
npm run test:e2e
```

## Primeiro passo no Codex

Leia nesta ordem:

1. `AGENTS.md`
2. `docs/product/vision.md`
3. `docs/product/functional-scope.md`
4. `docs/architecture/overview.md`
5. `docs/features/000-first-vertical-slice.md`
6. `docs/qa/definition-of-done.md`

Depois, execute os checks existentes antes de alterar qualquer arquivo.

## Observação sobre `package-lock.json`

O lockfile do Angular (`apps/admin/package-lock.json`) já foi gerado e versionado, e o CI usa `npm ci`. Ao adicionar ou atualizar dependências, rode `npm install` em `apps/admin` e reversione o lockfile resultante.
