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
