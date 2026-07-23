# AGENTS.md

## Missão

Desenvolver uma plataforma SaaS multiempresa simples para organizar uma única agenda profissional por organização, clientes, serviços, bloqueios, recorrências, lista de espera, contatos ainda não agendados, histórico administrativo e exportações.

A IA atua como par de programação. Ela não decide produto, não amplia escopo e não implementa o sistema inteiro de uma vez.

## Leia antes de trabalhar

- `docs/product/vision.md`
- `docs/product/functional-scope.md`
- `docs/architecture/overview.md`
- `docs/architecture/multi-tenancy.md`
- `docs/architecture/security.md`
- `docs/architecture/development-methodology.md`
- ADRs em `docs/decisions/`
- feature ativa em `docs/features/`
- `docs/qa/definition-of-done.md`

## Stack obrigatória

### Backend

- Java 25
- Spring Boot 4.1
- Maven
- Spring MVC, não WebFlux
- Spring Data JPA para transações comuns
- SQL nativo quando PostgreSQL representar melhor a regra
- Spring Security
- Spring Validation
- Flyway
- Spring Modulith
- PostgreSQL 18

### Frontend

- Angular 22
- standalone components
- TypeScript estrito
- Reactive Forms
- Signals e serviços por domínio
- Angular Material/CDK quando os componentes começarem
- FullCalendar Standard na fatia da agenda
- Vitest

## Arquitetura

- Monólito modular.
- Uma API e um banco compartilhado.
- Uma única agenda profissional por organização.
- Todas as entidades de negócio pertencem a uma organização.
- Nenhum repository ou caso de uso busca dados apenas pelo `id` quando o registro é multiempresa.
- O `organization_id` é derivado da sessão autenticada, nunca confiado a partir do corpo enviado pelo navegador.
- Dependências entre módulos devem ser verificadas pelo Spring Modulith.
- Implementar por fatias verticais pequenas.

## Módulos atuais

- `identity`: autenticação, sessão, recuperação e usuários.
- `organizations`: organizações, vínculo com usuárias, papéis e permissões.
- `membership`: convite, ativação, desativação e reativação de usuárias na organização (acima de `identity`/`organizations`/`auditing`/`shared.security` na hierarquia de dependências, para evitar ciclo).
- `clients`: clientes e prevenção de duplicidades.
- `catalog`: serviços oferecidos pela organização.
- `scheduling`: agendamentos, status, remarcações e recorrências.
- `availability`: horários de funcionamento, exceções e bloqueios.
- `waitlist`: lista de espera.
- `relationships`: contatos que ainda não agendaram e próxima ação.
- `auditing`: histórico administrativo e auditoria.
- `reporting`: indicadores e exportações não financeiras.
- `system`: endpoints técnicos mínimos.
- `shared`: tipos técnicos compartilhados, sem virar depósito de regras.

## Escopo proibido

Não implementar, nem preparar infraestrutura antecipada, para:

- prontuário, anamnese ou dados clínicos;
- prescrições, telemedicina ou documentos médicos;
- pagamentos, financeiro, estoque, comissões, repasses ou notas fiscais;
- WhatsApp, mensagens preenchidas ou automáticas;
- agenda pública ou autoagendamento;
- Google Calendar ou calendários externos;
- multiagenda, várias profissionais, salas ou equipamentos;
- integração de agendamento com sites;
- campanhas, CRM completo, lead scoring ou automação comercial;
- aplicativo móvel;
- microserviços, Kafka, RabbitMQ, Redis ou Kubernetes sem problema comprovado.

## Fluxo de trabalho

1. Ler a feature ativa e os ADRs.
2. Inspecionar o código relacionado.
3. Explicar a abordagem e os riscos de forma curta.
4. Escrever ou atualizar testes da regra.
5. Implementar apenas o necessário.
6. Refatorar sem misturar alterações não relacionadas.
7. Executar todos os checks.
8. Revisar o diff procurando vazamento multiempresa e aumento de escopo.
9. Atualizar documentação.
10. Entregar um incremento pequeno e publicável.

## Regras de segurança

- Negar acesso por padrão.
- Não armazenar JWT no `localStorage`.
- A autenticação final será por sessão e cookie seguro.
- Não desabilitar CSRF globalmente para facilitar desenvolvimento.
- Nunca registrar senha, token, cookie ou dados pessoais completos em logs técnicos.
- Toda exportação deve ser auditada.
- Toda ação de suporte sobre dados de cliente deve ser restrita e auditável.
- Sempre testar que Organização A não lê nem modifica dados da Organização B.
- Não retornar detalhes internos de exceções ao frontend.

## Regras de banco

- Alterações de schema exclusivamente via Flyway.
- Não usar `ddl-auto=update`; manter `validate`.
- Usar `TIMESTAMPTZ` para ocorrências concretas.
- Usar `LocalDate`/`LocalTime` para regras locais de funcionamento.
- Normalizar telefones antes de procurar duplicidades.
- Cancelamento preserva histórico; exclusão é excepcional.
- A proteção final contra sobreposição deve existir no PostgreSQL, não apenas no frontend.
- Avaliar RLS antes da primeira produção comercial, conforme ADR.

## Regras de frontend

- O frontend melhora a experiência, mas o backend decide validade e autorização.
- Não duplicar enums e contratos manualmente por longo prazo; gerar cliente TypeScript por OpenAPI quando a primeira API de negócio estabilizar.
- Manter páginas e componentes pequenos.
- Não adicionar NgRx sem estado complexo comprovado.
- Acessibilidade e navegação por teclado fazem parte da entrega.
- Não usar informação clínica em labels, exemplos ou campos livres.

## Testes mínimos por regra

- caso feliz;
- validação de entrada;
- autorização;
- isolamento por organização;
- concorrência quando aplicável;
- persistência e constraints quando aplicável;
- resposta de erro utilizável;
- fluxo de frontend crítico.

## Comandos

Backend:

```bash
cd apps/api
./mvnw verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend:

```bash
cd apps/admin
npm install
npm run test:ci
npm run build
npm start
```

Infra:

```bash
docker compose up -d
docker compose down
```

Checks agregados:

```bash
./scripts/check.sh
```

```powershell
./scripts/check.ps1
```

## Definition of Done resumida

Uma feature só está pronta quando:

- atende aos critérios de aceite;
- possui testes relevantes;
- passa no CI;
- respeita organização e permissões;
- possui migração segura, quando necessária;
- atualiza documentação;
- não adiciona funcionalidade não aprovada;
- pode ser demonstrada de ponta a ponta;
- não deixa TODO escondendo regra essencial.
