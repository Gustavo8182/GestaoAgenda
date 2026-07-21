# Agenda API

API Spring Boot organizada como monólito modular.

## Executar

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Testar

```bash
./mvnw verify
```

## Regras

- Não usar `ddl-auto=update`.
- Toda alteração de schema passa por Flyway.
- Entidades multiempresa carregam `organization_id`.
- Segurança é fechada por padrão.
- Não criar controllers diretamente ligados a repositories.
- Não transformar `shared` em depósito de regras de negócio.
