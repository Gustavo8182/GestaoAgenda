# Arquitetura

## Decisão central

Monólito modular com Angular separado, uma API Spring Boot e um PostgreSQL compartilhado.

```text
Navegador
  -> Angular
  -> /api
  -> Spring Boot modular
  -> PostgreSQL
```

## Motivos

- regras transacionais fortes;
- equipe pequena;
- implantação simples;
- consistência de dados;
- evolução rápida;
- baixo custo operacional;
- separação futura possível sem custo antecipado de microserviços.

## Pacotes de domínio

```text
br.com.agendaplatform
├── identity
├── organizations
├── membership
├── clients
├── catalog
├── scheduling
├── availability
├── waitlist
├── relationships
├── auditing
├── reporting
├── system
└── shared
```

Cada módulo deve expor contratos intencionais e manter detalhes internos privados.

## Contrato frontend/backend

Quando a primeira API de negócio estabilizar, o backend publicará OpenAPI e o cliente TypeScript será gerado. Até lá, evitar criar uma camada grande de DTOs duplicados no frontend.
