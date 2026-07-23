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

Implementado: `br.com.agendaplatform.organizations.CurrentOrganizationProvider` (bean por requisição) resolve a organização e o papel (`OrganizationRole`) da usuária autenticada a partir do `userId` do principal — nunca de dado enviado pelo navegador. Todo módulo de negócio (`catalog`, `clients`, `scheduling`, `availability`, `waitlist`, `relationships`, `reporting`, `membership`) injeta esse contrato para obter o `organizationId` usado em toda consulta/gravação, em vez de reimplementar a resolução. Assume no máximo um vínculo `ACTIVE` por usuária em qualquer momento — reforçado por constraint única parcial em banco desde a `V018` (ver `docs/architecture/security.md`, seção "Autorização").

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

**Avaliação feita em 2026-07-23** (parte da auditoria de prontidão para cliente-piloto, ver
`docs/operations/production-readiness.md`): isolamento hoje é 100% em nível de aplicação —
todo repository que expõe dados de negócio usa `findByIdAndOrganizationId`/
`findAllByOrganizationId...` (nunca busca só por `id`), e cada módulo tem pelo menos um teste
de isolamento multiempresa (ver seção "Testes obrigatórios" acima; confirmado presente em
`clients`, `catalog`, `scheduling`, `availability`, `waitlist`, `relationships`, `auditing` e
`reporting`/exportação nesta auditoria). RLS no Postgres **não foi implementada**.

Decisão: manter só a camada de aplicação por ora. Motivo — o risco que RLS mitiga
(alguém com acesso direto ao banco, fora da aplicação, conseguindo ler dados de outra
organização) não existe hoje: não há acesso de suporte/terceiros ao banco, nem múltiplas
equipes operando o mesmo banco. Adicionar RLS agora seria uma segunda camada de defesa para
uma ameaça ainda não presente, com custo real de complexidade (toda sessão de banco precisaria
setar uma variável de sessão com o `organization_id` atual, e cada política replicaria a mesma
regra já garantida pelos repositories).

Gatilhos para reavaliar (qualquer um destes deve disparar a implementação de RLS antes de
prosseguir):

- suporte de terceiros (ou qualquer pessoa fora do time de desenvolvimento) ganhar acesso
  direto ao banco de produção;
- um cliente ou auditoria de segurança exigir contratualmente uma segunda camada de isolamento;
- a arquitetura mudar para múltiplas equipes/serviços lendo o mesmo banco fora da API atual.
