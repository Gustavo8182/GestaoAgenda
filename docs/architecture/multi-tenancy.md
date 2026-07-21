# Multiempresa

## Modelo

- um banco;
- um schema;
- tabelas compartilhadas;
- `organization_id` obrigatório nas entidades operacionais;
- uma única agenda por organização.

## Regra de acesso

O contexto da organização vem da sessão do usuário e de sua associação em `organization_members`.

Nunca aceitar `organization_id` do corpo da requisição como fonte de autorização.

## Repositories

Consultas multiempresa devem usar organização e identificador:

```text
findByIdAndOrganizationId(id, organizationId)
```

Não criar métodos genéricos que permitam buscar registros de negócio apenas por `id` fora da camada autorizada.

## Testes obrigatórios

- Organização A não lê registro da B;
- Organização A não altera registro da B;
- exportação contém somente registros da organização;
- auditoria pertence à organização correta;
- identificador válido de outra organização resulta em não encontrado ou acesso negado, sem revelar dados.

## RLS

Row-Level Security será avaliada antes da primeira produção comercial como segunda barreira. Não substitui validação e autorização na aplicação.
