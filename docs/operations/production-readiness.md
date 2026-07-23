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
- **Autenticação e recuperação testadas.** Login por e-mail/senha, conta desativada, senha
  errada, usuária sem organização (`AuthControllerTest`). **Recuperação de senha implementada em
  2026-07-23** — era o maior gap encontrado nesta auditoria: `POST /api/v1/auth/password-reset/request`
  e `/confirm`, token de uso único com expiração de 60 minutos (`docs/architecture/security.md`
  já pedia exatamente isso), mensagem sempre genérica para não revelar se um e-mail está
  cadastrado. Validado de ponta a ponta contra uma instância real (Postgres + Mailpit): pedido →
  e-mail recebido → link com token → senha trocada → login com a senha nova funciona → login com
  a senha antiga falha → reuso do mesmo token falha.
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

- **Logs e alertas — nada além do padrão do Spring Boot.** Não há agregação de logs nem alertas
  configurados (ex.: Sentry, CloudWatch, Grafana). Depende de qual serviço o usuário quer usar
  em produção — decisão de ferramenta/orçamento, não técnica.

## Decisões de negócio/jurídicas (fora do que dá para resolver em código)

Alinhamento feito em 2026-07-23 (rodada de perguntas antes de desenhar bloqueios recorrentes) —
decisões provisórias registradas aqui, sujeitas a revisão contratual/jurídica antes da operação
comercial de verdade. Nenhuma delas foi implementada em código nesta rodada; são regras de
produto/negócio para orientar decisões técnicas futuras.

- **Banco gerenciado**: provedor ainda **100% em aberto** (não é Railway, Render, AWS RDS, Neon
  nem nenhum outro específico ainda). Decisão explícita: a aplicação deve continuar
  containerizável, configurável só por variáveis de ambiente, em PostgreSQL padrão, sem
  dependência proprietária de infraestrutura — para não travar a escolha do provedor depois.
  Quando o piloto se aproximar, comparar custo, backup, disponibilidade, região, banco gerenciado,
  logs, deploy e facilidade operacional antes de decidir.
- **Retenção após encerramento da organização**: regra provisória de produto adotada — **30 dias**
  de retenção após o encerramento. Durante esse período a conta fica desativada (sem acesso), os
  dados continuam preservados para exportação/restauração administrativa autorizada; depois do
  prazo, inicia-se exclusão/anonimização conforme a política definitiva (ainda não escrita).
  Backups têm ciclo de retenção próprio e podem expirar antes disso. **Não implementar exclusão
  automática por retenção nesta fase** — depende de uma especificação própria e revisão jurídica
  prévia; ver também item de backlog em `docs/product/roadmap.md`.
- **Exportação de encerramento** (processo, não só o CSV já implementado): depende de contrato
  com o cliente — já registrado como decisão consciente na rodada de exportação CSV (Fatia 6).
- **Contrato e termos de uso**: ainda não existem, nem estão em elaboração — pendente. Precisam
  cobrir assinatura, termos de uso, política de privacidade, responsabilidade sobre dados,
  exportação, cancelamento, retenção, exclusão, suporte e disponibilidade, antes de qualquer
  operação comercial de verdade.
- **Revisão LGPD**: ainda não existe responsável jurídico formal. Decisão: preparar um checklist
  **técnico** de apoio (minimização, finalidade, acesso, logs, exportação, retenção, exclusão,
  backups, suporte, dados de teste, incidentes, isolamento multiempresa) — explicitamente rotulado
  como apoio técnico, não parecer jurídico. A revisão jurídica formal continua sendo pré-requisito
  antes da comercialização; este agente não deve substituí-la.
- **Acesso de suporte restrito**: modelo alinhado (ver `docs/architecture/security.md`, seção
  "Autorização" — SUPPORT) — acesso excepcional, só à organização envolvida, preferencialmente
  somente leitura, temporário e auditado; nunca acesso operacional permanente. Ainda não
  implementado (SUPPORT continua sem nenhum acesso operacional hoje); sistema de tickets/aprovação
  formal fica para quando houver equipe de suporte de verdade — ver backlog.
- **Cliente-piloto**: candidata informal identificada (nome registrado só em `PROJECT_STATUS.md`,
  não neste documento nem na arquitetura) — ainda não confirmada, não deve influenciar nenhuma
  decisão de arquitetura ou regra de produto. O sistema continua genérico e multiempresa.
