# Produção — critérios futuros

Antes do primeiro cliente real. Auditoria técnica feita em 2026-07-23 (pedida explicitamente
antes de buscar um cliente-piloto): para cada item, o que já está pronto, o que falta e — quando
a resposta depende de uma decisão de negócio/jurídica em vez de código — por que não foi
resolvido sozinho aqui.

## Prontos

- **CSRF validado.** Ativo desde a Fase 1.1, exercitado em toda a suíte de testes de integração
  (cada requisição autenticada busca o cookie `XSRF-TOKEN` e envia `X-XSRF-TOKEN`) — centenas de
  execuções reais, não só teoria.
- **Testes multiempresa.** Todo módulo com dado sensível a organização tem pelo menos um teste
  `doesNotLeak...BetweenOrganizations` (clientes, serviços, agendamentos, disponibilidade, lista
  de espera, relacionamento, auditoria, exportação) — verificado de novo nesta auditoria,
  cobertura real, não apenas prometida.
- **Autenticação testada** (o que existe hoje): login por e-mail/senha, conta desativada, senha
  errada, usuária sem organização — todos cobertos em `AuthControllerTest`. Ver ressalva abaixo
  sobre a parte que falta.
- **Segredo fora do repositório.** `.env` está no `.gitignore` (só `.env.example`, com valores
  fictícios, é versionado); `application.yml` só usa `password: change-me` como *fallback* de
  desenvolvimento, sempre sobrescrevível por variável de ambiente (`DB_PASSWORD`). Confirmado
  nesta auditoria que nenhum segredo real está commitado em nenhum arquivo do repositório.
- **Logs sem dados pessoais.** Não existe nenhum `log.info/debug/warn` próprio no código da
  aplicação (só logging de framework); `application-local.yml` liga `DEBUG` para
  `br.com.agendaplatform`, mas isso é exclusivo do perfil `local` — a configuração base (usada em
  qualquer outro perfil) não ativa esse nível. Nenhum SQL com parâmetros é logado por padrão.
- **Cookie Secure e HTTPS — suporte já existe no código.** `server.servlet.session.cookie.secure`
  já é configurável via `SESSION_COOKIE_SECURE` (`application.yml`), `http-only: true` sempre.
  **O que falta não é código, é configuração de deploy**: setar `SESSION_COOKIE_SECURE=true` e
  colocar a API atrás de um proxy/load balancer que termine TLS (Spring Boot não serve HTTPS
  diretamente em produção nesse tipo de setup). Documentado nesta rodada em
  `docs/operations/deployment-checklist.md`.
- **Smoke tests automatizados.** O fluxo crítico (Playwright, `apps/admin/e2e/critical-flow.spec.ts`)
  já roda automaticamente no CI (job `e2e`) a cada push/PR. Além disso, adicionado nesta rodada
  um smoke test independente de código-fonte (`scripts/smoke-test.sh` / `.ps1`) que pode ser
  apontado para qualquer ambiente já no ar (local, homologação, produção) para confirmar que a
  API está de pé, respondendo e negando acesso sem sessão — útil justamente para o próximo item.

## Parcialmente prontos / decisão registrada

- **RLS avaliada.** Avaliação formal registrada em `docs/architecture/multi-tenancy.md`: hoje o
  isolamento é 100% em nível de aplicação (todo repository filtra por `organization_id`,
  confirmado por testes em cada módulo). Row-Level Security do Postgres **não foi implementada**
  — decisão consciente de manter simples enquanto o número de organizações e o risco percebido
  são pequenos (uma secretária por organização, sem acesso direto ao banco por terceiros).
  Gatilhos para reavaliar: acesso de suporte de terceiros ao banco, exigência contratual/auditoria
  de segurança de um cliente, ou crescimento que justifique uma segunda camada de defesa.
- **Plano de rollback — documentado, não testado em produção real** (não existe produção ainda).
  Ver `docs/operations/deployment-checklist.md`, seção "Rollback".
- **Backups e restauração — procedimento documentado, não testado contra um ambiente real.**
  Ver `docs/operations/deployment-checklist.md`, seção "Backup e restauração". Testar de verdade
  exige um ambiente de homologação com dados reais, que ainda não existe.
- **Migrações em ambiente de homologação.** Todas as 13 migrações já foram aplicadas com sucesso
  repetidas vezes contra Postgres real (Testcontainers, desenvolvimento local, validação de
  Docker) — o que falta é especificamente um ambiente de *homologação* separado do de
  desenvolvimento, que é uma decisão de infraestrutura (onde hospedar), não algo para decidir
  sozinho aqui.

## Pendências reais (gaps genuínos, não apenas configuração)

- **Recuperação de senha — funcionalidade que falta implementar.** `docs/product/functional-scope.md`
  lista "recuperação de senha" no escopo mínimo (MVP) e `docs/architecture/security.md` descreve
  "token de uso único e expiração" — mas isso **nunca foi construído**. Só existe login;
  não há "esqueci minha senha", geração de token, e-mail (via Mailpit em dev) nem troca de senha
  sem estar autenticada. Diferente dos outros itens desta lista, este não é uma configuração de
  deploy nem uma decisão de negócio — é uma fatia de funcionalidade do tamanho das outras já
  implementadas (recorrência, lista de espera, relacionamento). **Não implementado nesta
  auditoria** por ser uma decisão de escopo/priorização, não uma correção segura de se fazer
  no meio de uma auditoria — recomendo tratar como a próxima fatia antes do piloto, já que um
  cliente real eventualmente vai esquecer a senha.
- **Logs e alertas — nada além do padrão do Spring Boot.** Não há agregação de logs nem alertas
  configurados (ex.: Sentry, CloudWatch, Grafana). Depende de qual serviço o usuário quer usar
  em produção — decisão de ferramenta/orçamento, não técnica.

## Decisões de negócio/jurídicas (fora do que dá para resolver em código)

- **Banco gerenciado**: qual provedor (RDS, Cloud SQL, Neon etc.) é decisão de infraestrutura e
  custo.
- **Política de retenção** e **exportação de encerramento** (processo, não só o CSV já
  implementado): dependem de contrato com o cliente — já registrado como decisão consciente na
  rodada de exportação CSV (Fatia 6).
- **Contrato e termos de uso**: jurídico.
- **Revisão LGPD**: precisa de análise jurídica; o que é *tecnicamente* verificável (dados
  mínimos, sem prontuário/dados de saúde, observações só administrativas) já é seguido pelo
  código, mas a revisão formal em si não é algo que este agente deva concluir sozinho.
- **Acesso de suporte restrito**: política organizacional (quem na equipe tem acesso a produção,
  como é revogado, como é auditado) — não é uma configuração de código.
