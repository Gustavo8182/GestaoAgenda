# Desenvolvimento local

## Dependências

```bash
docker compose up -d
```

## API

```bash
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Painel

```bash
cd apps/admin
npm install
npm start
```

## Login local

O perfil `local` aplica uma migração adicional (`db/dev-seed`, ativa apenas nesse perfil) com uma usuária fictícia:

- e-mail: `dona@exemplo.test`;
- senha: `TrocarSenha123!`.

Essa usuária nunca é criada em outros perfis (produção não carrega `db/dev-seed`).

Os seeds de dev usam números de versão altos (`V900`, `V901`, ...) para nunca colidir com migrações reais. Isso tem um efeito colateral local: se o volume do Postgres já tiver aplicado esses seeds e uma nova migração real for adicionada com número menor (ex.: `V003` depois de `V901` já aplicada), o Flyway recusa por estar "fora de ordem". Nesse caso, rode `docker compose down -v` (apaga só o banco local, descartável) e suba de novo — não é um problema em produção, onde as migrações aplicam em ordem desde o início.

## E2E (Playwright)

Cobre o fluxo crítico da Feature 000: login, cadastro de serviço e cliente, criação de
agendamento e bloqueio de sobreposição — contra a API e o Postgres reais, não mockado.

Pré-requisitos (nessa ordem, cada um numa aba/processo separado):

```bash
docker compose up -d
cd apps/api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd apps/admin && npm start
```

Com os três rodando:

```bash
cd apps/admin
npm run test:e2e
```

O teste usa nomes únicos (timestamp) para serviço e cliente, e varia o horário do
agendamento a cada execução, para não colidir com dados de execuções anteriores no
mesmo banco de desenvolvimento. Não está incluído no CI ainda (exigiria orquestrar
Postgres + API + painel no pipeline) — rodar localmente antes de publicar mudanças que
afetem o fluxo de login, catálogo, clientes ou agenda.

## Banco

- host: `localhost`;
- porta: `5432`;
- banco: `agenda`;
- usuário: `agenda`;
- senha: definida em `.env`.

## E-mail local

Mailpit recebe futuros e-mails de recuperação e convite:

- SMTP: `localhost:1025`;
- interface: `http://localhost:8025`.

## Limpeza completa

```bash
docker compose down -v
```

Esse comando apaga o banco local.
