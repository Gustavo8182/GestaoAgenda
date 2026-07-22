# Status

## Fundação inicial

- [x] estrutura do repositório;
- [x] API Spring Boot;
- [x] módulos delimitados;
- [x] migração inicial;
- [x] painel Angular navegável;
- [x] Compose local;
- [x] CI inicial;
- [x] documentação para agentes;
- [x] primeira fatia especificada;
- [x] lockfile npm gerado em ambiente com internet;
- [x] build completo validado com JDK 25 e Node compatível;
- [x] repositório Git criado;
- [ ] cliente-piloto validado;
- [x] primeira fatia implementada (Feature 000, todas as 8 etapas — ver seção abaixo).

## Feature 000 — Primeira fatia vertical

Progresso por etapa (ver `docs/features/000-first-vertical-slice.md` e `docs/architecture/security.md`):

- [x] 1. Sessão e autenticação — login por e-mail/senha, sessão via Spring Session JDBC, CSRF ativo, logout, `/api/v1/auth/me`, guard e página de login no painel.
- [x] 2. Contexto da organização — resolução da organização/papel da usuária autenticada, exposta em `/login` e `/me`.
- [x] 3. Serviço — cadastro mínimo (nome + duração), escopado por organização, com auditoria.
- [x] 4. Cliente — cadastro mínimo (nome + telefone normalizado), aviso de duplicidade não bloqueante.
- [x] 5. Agendamento e constraint de sobreposição — validado na aplicação e no PostgreSQL (`EXCLUDE`), com teste de concorrência real.
- [x] 6. Lista no frontend — feita junto de cada etapa (Serviços, Clientes, Agenda já têm formulário e lista reais).
- [x] 7. Auditoria — feita junto de cada etapa (`SERVICE_CREATED`, `CLIENT_CREATED`, `APPOINTMENT_CREATED`).
- [x] 8. E2E — Playwright cobrindo login, serviço, cliente, agendamento e bloqueio de conflito, contra API e Postgres reais.

Detalhes técnicos da etapa 1: usuária autenticada por e-mail/senha (`br.com.agendaplatform.identity`), senha com `DelegatingPasswordEncoder` (bcrypt), sessão persistida em `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` (Flyway `V002`), CSRF por cookie (`XSRF-TOKEN`/`X-XSRF-TOKEN`), conta `DISABLED`/`INVITED` não autentica. Seed de desenvolvimento (`db/dev-seed`, perfil `local` apenas) cria `dona@exemplo.test` / `TrocarSenha123!` — ver `docs/operations/local-development.md`.

Detalhes técnicos da etapa 2: módulo `organizations` mapeia `organizations`/`organization_members` (JPA); `CurrentOrganizationProvider` (bean por requisição, `br.com.agendaplatform.organizations`) resolve a organização ativa a partir do `userId` do principal autenticado — nunca de dado enviado pelo navegador. O `userId` chega ao principal via `IdentityUserDetails`, que implementa o contrato compartilhado `shared.security.AuthenticatedPrincipal` (evita o módulo `organizations` acessar internals de `identity`). Usuária sem vínculo ativo com nenhuma organização recebe 403 mesmo com credenciais corretas. `/api/v1/auth/login` e `/api/v1/auth/me` agora retornam `{ user, organization }`. Seed de dev (`V901`) cria a organização de demonstração com a usuária como `OWNER`.

Detalhes técnicos da etapa 3 (escopo original da fatia 000): módulo `catalog` (`services`, Flyway `V003`) com `POST/GET /api/v1/catalog/services` (nome + duração em minutos). O `organizationId` sempre vem do `CurrentOrganizationProvider`, nunca do corpo da requisição. Criado o módulo `auditing` com o contrato `AuditRecorder` (grava quem/quando/o quê em `audit_logs`; metadata JSON ainda não mapeada, para não arriscar incompatibilidade Hibernate 7 + Jackson 3 não testada); toda criação de serviço grava `SERVICE_CREATED`. Extraído `shared.security.CurrentActorProvider`, reaproveitado pelo `CurrentOrganizationProvider` e pela auditoria. Painel ganhou formulário e lista reais em "Serviços". (Cor, ordem, inativação e exigência de confirmação foram adicionados depois — ver seção "Ampliação: Serviços" abaixo.)

Ressalva de ambiente local: os seeds de dev usam versões Flyway altas (`V900+`) para nunca colidir com migrações reais; se o volume local do Postgres já tiver esses seeds aplicados, uma migração nova numerada abaixo de 900 (como `V003`) fica "fora de ordem" até rodar `docker compose down -v`. Detalhes em `docs/operations/local-development.md`.

Detalhes técnicos da etapa 4 (escopo original da fatia 000): módulo `clients` (`clients`, Flyway `V004`) com `POST/GET /api/v1/clients` (nome + telefone). `PhoneNormalizer` remove tudo que não é dígito e só descarta o código do país "55" quando sobrarem 12–13 dígitos (nunca confunde com um DDD "55" legítimo, que tem 10–11 dígitos). Duplicidade é verificada por telefone normalizado *dentro da mesma organização* e apenas gera aviso (`possibleDuplicate`) — nunca bloqueia o cadastro, conforme regra de que duas pessoas podem compartilhar o mesmo número. Toda criação registra `CLIENT_CREATED` via `AuditRecorder`. Painel ganhou formulário e lista reais em "Clientes", com banner de aviso quando a API sinaliza duplicidade. (Origem, observações, telefone alternativo e busca foram adicionados depois — ver seção "Ampliação: Clientes" abaixo. Restrição de contato e ativo/inativo continuam fora do escopo.)

Detalhes técnicos da etapa 5: módulo `scheduling` (`appointments`, Flyway `V005`) com `POST/GET /api/v1/appointments`. Uma única agenda por organização (ADR 0002): a constraint `EXCLUDE USING gist (organization_id WITH =, tstzrange(start_at, end_at) WITH &&)` (usa `btree_gist`, já habilitada na V001) impede qualquer sobreposição na mesma organização, independente de cliente ou serviço; validado com teste de concorrência real (duas threads competindo para inserir o mesmo horário — exatamente uma é aceita). A aplicação também verifica sobreposição antes de gravar, para dar uma mensagem clara (409) em vez de deixar o erro cru do banco vazar; o `DataIntegrityViolationException` da constraint continua mapeado para 409 como rede de segurança final contra corrida. Novos contratos públicos mínimos `catalog.ServiceLookup` e `clients.ClientLookup` permitem ao `scheduling` confirmar que cliente/serviço pertencem à organização atual e obter o nome para exibição, sem acessar internals desses módulos. Painel ganhou a página "Agenda" com lista simples (sem calendário visual, fora do escopo desta fatia) — a duração do agendamento é calculada a partir do serviço escolhido.

Detalhes técnicos da etapa 8: `@playwright/test` adicionado como dependência de desenvolvimento do painel (`apps/admin/playwright.config.ts`, `apps/admin/e2e/critical-flow.spec.ts`, script `npm run test:e2e`). O teste cobre login → cadastro de serviço → cadastro de cliente → criação de agendamento → bloqueio de sobreposição, direto contra a API e o Postgres reais (sem mocks). Usa nomes e horários únicos por execução para não colidir com dados de rodadas anteriores no mesmo banco de desenvolvimento. **Ressalva**: ainda não está integrado ao CI (exigiria orquestrar Postgres + API + painel no pipeline); por ora é uma verificação manual antes de publicar mudanças que afetem login, catálogo, clientes ou agenda — ver `docs/operations/local-development.md`.

## Ampliação: Clientes (pós Feature 000)

Adiciona ao módulo `clients` (Flyway `V006`) os campos telefone alternativo, origem e observações administrativas, além de busca por nome ou telefone — pedidos explicitamente do escopo funcional completo (seção "Clientes"), fora da fatia mínima da Feature 000.

- **Telefone alternativo**: mesma validação/normalização do telefone principal (`PhoneNormalizer`, 10–11 dígitos); opcional.
- **Origem** e **observações administrativas**: texto livre, sem vocabulário fechado (decisão registrada nesta sessão — evita migração futura se a lista de valores mudar).
- **Duplicidade**: passou a considerar os dois telefones — o normalizado do novo cadastro (principal e alternativo, se houver) é comparado contra o principal *e* o alternativo de todas as clientes da organização (`ClientRepository.existsAnyWithNormalizedPhoneIn`).
- **Busca**: um único parâmetro `query` (`GET /api/v1/clients?query=...`) compara contra nome (parte do nome, case-insensitive) OU telefone normalizado (principal/alternativo, incluindo últimos dígitos); sem dígitos na busca, o lado de telefone é ignorado para não casar tudo por engano.
- Sem edição ou ativação/inativação de cliente — não foi pedido nesta rodada.

Painel: formulário de cliente ganhou os três campos novos; lista mostra telefone alternativo, origem (como tag) e observações quando presentes; campo de busca com debounce de 300ms.

Validado com Postgres real no navegador: cadastro com todos os campos, busca por nome parcial e por dígitos de telefone, e duplicidade detectada quando o telefone alternativo de uma nova cliente coincide com o principal de outra já cadastrada.

## Ampliação: Serviços (pós Feature 000)

Adiciona ao módulo `catalog` (Flyway `V007`) os campos cor, ordem de exibição, exigência de confirmação e inativação — pedidos explicitamente do escopo funcional completo (seção "Serviços"), fora da fatia mínima da Feature 000.

- **Cor**: opcional, validada no formato hexadecimal `#RRGGBB` (`@Pattern`); `null` permanece válido (campo não obrigatório).
- **Ordem de exibição**: opcional na requisição; quando omitida, é calculada automaticamente como `max(ordem existente na organização) + 1` (`ServiceRepository.nextDisplayOrder`), então novos serviços aparecem no fim da lista por padrão. Lista ordenada por `display_order` e depois nome.
- **Exige confirmação**: booleano, `false` por padrão.
- **Inativação**: uma ação real (`POST /api/v1/catalog/services/{id}/deactivate`), não só uma coluna passiva — segue a regra do AGENTS.md de nunca apagar serviço já usado, só inativar para preservar histórico. `GET /services` continua retornando ativos *e* inativos (com a flag `active`), já que o histórico precisa continuar visível; o módulo `scheduling` não foi alterado (permanece podendo resolver nome de serviços inativos para agendamentos antigos). A página "Agenda" filtra a lista para mostrar só serviços ativos no dropdown de novo agendamento — um filtro só no frontend, sem tocar no backend de `scheduling`.
- Sem reativação nem edição de serviço — não foi pedido nesta rodada.

**Bug real encontrado e corrigido**: o `CreateServiceRequest` (Java record) tinha um construtor auxiliar de 2 argumentos para compatibilidade com os testes já existentes, e o campo opcional `requiresConfirmation` era um `boolean` primitivo. Ao enviar um JSON de verdade com só os campos obrigatórios (sem passar pelo construtor Java, que sempre preenche todos os campos do record antes de serializar), o Jackson tentava mapear o campo ausente como `null` para um `boolean` primitivo e falhava com `Cannot map 'null' into type 'boolean'` — um 400 sem nenhuma mensagem útil. Corrigido trocando para `Boolean` (boxed, aceita `null`) com um método `requiresConfirmationOrDefault()` resolvendo o padrão `false`. Esse bug só aparece com JSON parcial de verdade — testes que serializam o próprio record Java (`objectMapper.writeValueAsString(new CreateServiceRequest(...))`) nunca o pegam, porque sempre produzem um JSON completo. Adicionado teste de regressão (`acceptsRawJsonPayloadWithOnlyTheRequiredFields`) que envia uma string JSON literal com só os campos obrigatórios. **Lição para requests futuras**: campos opcionais em records usados como corpo de requisição devem ser tipos boxed (`Boolean`, `Integer`), nunca primitivos.

Painel: formulário de serviço ganhou seletor de cor, ordem de exibição (opcional) e checkbox "Exige confirmação"; lista mostra a cor como um indicador visual, tags de "Exige confirmação"/"Inativo", e botão "Inativar" por serviço ativo.

Validado via curl contra Postgres real (a extensão do Chrome ficou instável nesta sessão — aba travada e eventos de clique não registrando mesmo em aba nova; ver ressalva abaixo): cadastro com cor/ordem/confirmação, ordem automática incrementando corretamente, inativação registrando auditoria (`SERVICE_DEACTIVATED`) e mantendo o serviço na listagem com `active: false`.

**Ressalva**: a validação visual no navegador (Claude in Chrome) não foi possível nesta sessão por instabilidade da extensão (erro "Browser extension is not connected", aba sem resposta a scripts). A validação funcional foi feita via curl contra a API real e via os testes automatizados (backend e frontend), mas o fluxo específico desta ampliação não foi clicado manualmente na UI.

## Remarcação e cancelamento de agendamentos (pós Feature 000)

Adiciona ao módulo `scheduling` (Flyway `V008`) a capacidade de remarcar e cancelar agendamentos — a Feature 000 só cobria criação e listagem.

- **Status**: novo campo `status` (`SCHEDULED`/`CANCELLED`) no `Appointment`. A constraint `appointments_no_overlap` (V005) foi recriada como **EXCLUDE parcial** (`WHERE status = 'SCHEDULED'`): sem isso, cancelar um agendamento nunca liberaria o horário, já que a constraint original considerava todas as linhas, inclusive as canceladas. Validado com teste no nível de banco (`AppointmentOverlapConstraintTest.cancellingAnAppointmentFreesUpTheSlotAtTheDatabaseLevel`) e ponta a ponta via API.
- **Remarcar** (`POST /api/v1/appointments/{id}/reschedule`): atualiza `start_at`/`end_at` do mesmo registro (mesmo id, não cria um novo agendamento); revalida sobreposição excluindo o próprio registro (`AppointmentRepository.existsOverlappingExcluding`); não permite remarcar um agendamento já cancelado (`InvalidAppointmentStateException`, 400).
- **Cancelar** (`POST /api/v1/appointments/{id}/cancel`): exige `reason` (`@NotBlank`) para manter o histórico útil; marca `status = CANCELLED` e grava `cancellation_reason`; não permite cancelar duas vezes (400). Nunca apaga a linha — preserva histórico conforme AGENTS.md.
- **Auditoria com metadata**: até aqui o `AuditRecorder` só gravava quem/quando/o quê, sem detalhe (a coluna `metadata JSONB` da V001 nunca tinha sido mapeada, por risco não testado de Hibernate 7 + Jackson 3 — ver nota da etapa 3). Resolvido nesta rodada: `AuditLog.metadata` mapeado como `Map<String, String>` com `@JdbcTypeCode(SqlTypes.JSON)`, validado com Postgres real (Testcontainers). `APPOINTMENT_RESCHEDULED` grava horário anterior e novo; `APPOINTMENT_CANCELLED` grava o motivo — como pedido no AGENTS.md ("quem remarcou; data e horário anteriores; data e horário novos; quem cancelou; motivo do cancelamento"). O contrato `AuditRecorder` ganhou uma sobrecarga com `metadata`; a sobrecarga antiga (sem metadata) continua existindo para as chamadas que não precisam dela.
- Sem lista de espera, confirmação manual ou os demais status do ciclo completo (chegou, em atendimento, realizado, não compareceu) — fora do escopo pedido nesta rodada.

Painel: cada linha de agendamento ativo ganhou botões "Remarcar" e "Cancelar", cada um abrindo um mini-formulário inline na própria linha (sem modal). Remarcar preserva a duração original do agendamento (calculada a partir do `startAt`/`endAt` atuais, não depende de re-selecionar o serviço). Agendamentos cancelados continuam na lista (histórico), com estilo tracejado e o motivo exibido em vermelho.

Validado ponta a ponta: suíte completa do backend (`./mvnw clean verify`, 46 testes, incluindo o `ArchitectureTest` de módulos), suíte do frontend (`ng test`, todos os specs), curl contra API + Postgres reais (criar → remarcar → conflito bloqueado → cancelar com motivo → horário liberado → cancelamento duplo rejeitado → auditoria com os dados corretos), e desta vez também na UI real no navegador (login → Agenda → remarcar → cancelar), sem a instabilidade do Claude in Chrome que tinha bloqueado a validação visual da ampliação de Serviços.

## Horários de funcionamento e bloqueios (pós Feature 000)

Novo módulo `availability` (já previsto no AGENTS.md, com scaffold vazio criado na Fase 1.1) com duas capacidades: horário de funcionamento semanal e bloqueios pontuais. Ambos passam a ser validados na criação e remarcação de agendamentos (`scheduling`), reaproveitando a infraestrutura já existente de checagem de conflito.

- **Horário de funcionamento** (Flyway `V009`, tabela `business_hours`): um intervalo por dia da semana (`day_of_week` + `start_time`/`end_time`, únicos por organização). `PUT /api/v1/availability/business-hours` **substitui inteiramente** a configuração anterior a cada chamada (semântica de "salvar a semana toda"), em vez de criar/editar/remover dias individualmente — mais simples e sem risco de estado parcial inconsistente. **Comportamento padrão preservado**: se a organização nunca configurou horários (tabela vazia), não há nenhuma restrição — o comportamento é idêntico ao que existia antes deste recurso. Só passa a haver restrição depois que a organização configura ao menos um dia; dias não configurados nesse ponto em diante contam como fechados.
- **Bloqueios** (Flyway `V009`, tabela `blocks`): período livre (`start_at`/`end_at` + `reason` obrigatório) em que a organização não atende (feriado, compromisso pessoal, etc.). `POST/GET /api/v1/availability/blocks` e `DELETE /api/v1/availability/blocks/{id}`. Remover um bloqueio é exclusão real (não há "histórico de negócio" a preservar aqui, diferente de cliente/serviço/agendamento), mas a remoção é auditada (`BLOCK_REMOVED`, com motivo e horário nos metadata) antes de apagar a linha.
- **Validação em `scheduling`**: `AppointmentScheduler.create()`/`.reschedule()` agora checam, nesta ordem, (1) se o horário está dentro do funcionamento configurado (`OutsideBusinessHoursException`, 400) e (2) se não sobrepõe um bloqueio (`BlockedTimeException`, 409, código `blocked_time` — distinto do conflito entre agendamentos). Novo contrato público mínimo `availability.AvailabilityCheck` (implementado em `availability.infrastructure`), no mesmo padrão de `ServiceLookup`/`ClientLookup`. `organizations.CurrentOrganization` ganhou o campo `timezone` (já existia na tabela `organizations` desde a V001, nunca tinha sido exposto) para converter o horário do agendamento (`Instant`, UTC) para o dia da semana e hora local da organização antes de comparar com `business_hours`.
- **Limitação conhecida e aceita nesta rodada**: a proteção contra sobreposição agendamento-vs-agendamento tem uma constraint `EXCLUDE` no PostgreSQL como rede de segurança final contra concorrência (ADR 0006); agendamento-vs-bloqueio só tem checagem na aplicação, sem equivalente no banco (uma exclusão cruzando duas tabelas não é direta em Postgres). Dado o volume de uso esperado (uma secretária por organização, não um sistema de alta concorrência), o risco de duas requisições colidindo exatamente nesse instante é considerado aceitável por ora; documentado aqui para revisão futura caso o padrão de uso mude.
- Sem exceções pontuais ao horário semanal (ex.: "só essa sexta abre até mais tarde") — isso é resolvido criando um bloqueio ou não, não uma feature separada nesta rodada. Sem UI de calendário para os bloqueios (aparecem em lista simples, junto do horário de funcionamento, na página "Configurações").

Painel: página "Configurações" ganhou duas seções reais — grade dos 7 dias da semana (marcar/desmarcar + horário de início/fim) com botão "Salvar horários", e uma lista de bloqueios com formulário de criação (início, fim, motivo) e botão "Remover" por item. A página "Agenda" ganhou mensagens de erro específicas por código (`blocked_time`, `invalid_appointment`) em vez do texto genérico anterior — encontrado durante a validação manual no navegador: a UI mostrava "Não foi possível criar o agendamento" tanto para um horário bloqueado quanto para qualquer outro erro, quando a API já retornava uma mensagem específica.

Validado com Postgres real: suíte completa do backend (`./mvnw clean verify`), incluindo `ArchitectureTest` (o novo módulo `availability` não viola limites do Spring Modulith) e testes dedicados de agendamento fora do horário configurado, dentro do horário configurado, sobreposição com bloqueio na criação e na remarcação; suíte do frontend (`ng test`); validação manual via curl; e validação na UI real no navegador (configurar horário de sábado, criar bloqueio, tentar agendar dentro do bloqueio e ver a mensagem "Este horário está bloqueado.").

## Dashboard diário (pós Feature 000)

Novo módulo `reporting` (já previsto no AGENTS.md, com scaffold vazio criado na Fase 1.1) com o primeiro indicador: `GET /api/v1/dashboard`, agregando dados de `scheduling` e `availability` sem nenhum novo dado próprio (não há migração Flyway nesta rodada).

- **Conteúdo**: agendamentos de hoje (todos os status, para preservar visibilidade do que foi desmarcado ou não compareceu), próximo atendimento (o próximo agendamento ainda não cancelado/realizado/com falta a partir de agora, não necessariamente hoje), bloqueios de hoje e um resumo da semana atual (segunda a domingo, fuso da organização) com contagem por status.
- **Nota histórica (já superada)**: nesta rodada, "confirmações pendentes e faltas" ainda não existiam no domínio (`Appointment` só tinha `SCHEDULED`/`CANCELLED`). A ampliação "Ciclo completo de status do agendamento" (ver seção abaixo) implementou exatamente isso; o `week` do dashboard e a lista "Agenda de hoje" já refletem os novos status.
- **Novos contratos públicos mínimos**: `scheduling.AppointmentOverview` (busca por intervalo de datas e "próximo agendamento") e `availability.BlockLookup` (bloqueios sobrepondo um intervalo), no mesmo padrão de `ServiceLookup`/`ClientLookup`. Isso exigiu mover `AppointmentSummary` e `BlockSummary` dos pacotes internos (`scheduling.application`, `availability.application`) para os pacotes raiz dos módulos — eram DTOs só usados internamente até agora, e o Spring Modulith só expõe pacotes raiz por padrão.
- **"Agora" e "hoje" testáveis sem depender do relógio real**: o `DashboardService` usa o `Clock` já injetado (`shared.ClockConfig`, `Clock.systemUTC()` em produção); os testes substituem por um `Clock.fixed(...)` via `@TestConfiguration`/`@Primary`, evitando testes instáveis perto da meia-noite ou de virada de semana.

Painel: página "Dashboard" deixou de ser um placeholder — cinco cards (próximo atendimento em destaque, resumo da semana, agenda de hoje, bloqueios de hoje, ações rápidas com atalhos para Agenda e Configurações).

Validado com Postgres real: suíte completa do backend (`./mvnw clean verify`, 62 testes, incluindo `ArchitectureTest`), suíte do frontend (`ng test`), curl contra API real (incluindo inserção direta de dados de teste no banco, já que os horários de funcionamento configurados em rodadas anteriores restringiam o dia da validação) e conferência visual no navegador — todos os cinco cards renderizando os dados esperados. **Ressalva**: a extensão Claude in Chrome desconectou no meio da validação (mesma instabilidade já registrada nas ampliações anteriores) antes de clicar nos links de "Ações rápidas"; dado que são links `routerLink` simples já cobertos pela navegação testada em outras páginas, não foi considerado necessário insistir.

## Ciclo completo de status do agendamento (pós Feature 000)

Fecha a Fatia 2 do roadmap ("operação diária"): além de agendado/cancelado, o agendamento agora percorre confirmado, chegou, em atendimento e realizado, além de falta — exatamente o que a especificação funcional pedia em "confirmar manualmente no sistema" e "registrar chegada, realização e falta" (Flyway `V010`).

- **Novos status**: `CONFIRMED`, `ARRIVED`, `IN_PROGRESS`, `DONE`, `NO_SHOW`, além dos já existentes `SCHEDULED`/`CANCELLED`.
- **Transições e regras** (todas em `Appointment`, cada uma validando o estado atual e lançando `InvalidAppointmentStateException` com mensagem específica caso inválida):
  - `confirmar`: só a partir de `SCHEDULED`.
  - `registrar chegada`: a partir de `SCHEDULED` ou `CONFIRMED`.
  - `iniciar atendimento`: só a partir de `ARRIVED`.
  - `concluir` (realizado): a partir de `ARRIVED` ou `IN_PROGRESS`.
  - `registrar falta`: a partir de `SCHEDULED` ou `CONFIRMED` (não faz sentido depois que a cliente já chegou).
  - `cancelar`: passou a ser bloqueado se o agendamento já está `DONE` ou `NO_SHOW` (antes só bloqueava se já `CANCELLED`).
  - `remarcar`: passou a exigir `SCHEDULED` ou `CONFIRMED` (antes aceitava qualquer coisa que não fosse `CANCELLED`; agora também bloqueia a partir de `ARRIVED` em diante, já que não faz sentido remarcar um atendimento que já começou).
- **Conflito de horário**: a constraint `EXCLUDE` do PostgreSQL (V005/V008, ADR 0006) e a checagem da aplicação foram generalizadas de "`WHERE status = 'SCHEDULED'`" para "`WHERE status NOT IN ('CANCELLED', 'NO_SHOW')`" — ou seja, `CONFIRMED`/`ARRIVED`/`IN_PROGRESS`/`DONE` continuam ocupando o horário (fazia sentido antes só considerar `SCHEDULED`, mas agora que existem mais status "ativos" todos eles precisam contar); só cancelamento e falta liberam o horário para um novo agendamento.
- **"Próximo atendimento" do dashboard** também foi generalizado: antes só considerava `SCHEDULED`, agora considera qualquer status que não seja `CANCELLED`, `NO_SHOW` ou `DONE` (ou seja, inclui `CONFIRMED`/`ARRIVED`/`IN_PROGRESS`).
- **Endpoints novos**: `POST /api/v1/appointments/{id}/confirm`, `/arrive`, `/start`, `/complete`, `/no-show` — todos sem corpo, seguindo o padrão já usado em `/cancel`/`/reschedule`, cada um auditado (`APPOINTMENT_CONFIRMED`, `APPOINTMENT_ARRIVED`, `APPOINTMENT_STARTED`, `APPOINTMENT_COMPLETED`, `APPOINTMENT_NO_SHOW`).
- **Resumo semanal do dashboard** ampliado de 2 para 4 contadores: `scheduledCount` (ainda em andamento: agendado/confirmado/chegou/em atendimento), `completedCount` (realizado), `cancelledCount`, `noShowCount`.

Painel: a página "Agenda" ganhou botões contextuais por linha — só aparecem as ações válidas para o status atual (ex.: "Iniciar atendimento" só aparece depois de "Registrar chegada"; "Remarcar"/"Cancelar" somem quando o agendamento já foi concluído/cancelado/com falta). Cada status ganhou uma etiqueta visual (Confirmado, Chegou, Em atendimento, Realizado, Não compareceu). O Dashboard também exibe essas etiquetas na lista "Agenda de hoje" e o card "Resumo da semana" ganhou mais dois números (realizados, faltas).

Validado com Postgres real: suíte completa do backend (`./mvnw clean verify`), incluindo `ArchitectureTest` e testes cobrindo cada transição de status, as transições inválidas (ex.: iniciar atendimento sem chegada registrada, remarcar um agendamento com chegada registrada, cancelar um já realizado) e a liberação do horário após falta; suíte do frontend (`ng test`) cobrindo a exibição contextual dos botões e o fluxo confirmar → chegar → iniciar → concluir; e validação manual completa na UI real do navegador (confirmar → registrar chegada → concluir refletido corretamente na Agenda e no Dashboard).

## Histórico do cliente (pós Feature 000)

Primeira fatia da "Fatia 3" do roadmap ("recorrência e histórico") — só a parte de histórico; recorrência semanal/quinzenal fica para uma rodada dedicada, dado o tamanho e risco maior (séries, conflitos por ocorrência, editar uma ocorrência vs. a série toda).

- **Nova consulta**: `GET /api/v1/appointments?clientId=...` — reaproveita o endpoint de listagem já existente (`AppointmentController.list`), agora aceitando um parâmetro opcional em vez de criar uma rota aninhada nova. Sem parâmetro, comportamento idêntico ao anterior (lista tudo da organização). Ordenado do mais recente para o mais antigo (histórico), diferente da lista geral (que é cronológica crescente).
- Sem migração nova — a consulta já existia como capacidade natural sobre os dados de `appointments`; só faltava expor.
- **Isolamento multiempresa**: como a consulta já filtra por `organization_id` E `client_id` juntos, pedir o histórico de uma cliente de outra organização simplesmente retorna lista vazia (nunca vaza dados) — mesmo padrão já usado em outras consultas cross-tenant.

Painel: a página "Clientes" ganhou um botão "Ver histórico" por cliente, que expande uma lista com todos os agendamentos dela (qualquer status, com a mesma etiqueta visual — Realizado, Cancelado com motivo, Não compareceu etc. — já usada na Agenda e no Dashboard), carregada sob demanda (só na primeira vez que a cliente é expandida).

Validado com Postgres real: suíte completa do backend (`./mvnw clean verify`), incluindo teste de isolamento multiempresa e de ordenação (mais recente primeiro); suíte do frontend (`ng test`) cobrindo expandir/recolher o histórico.

## Histórico de alterações / auditoria (pós Feature 000)

Fecha a parte de "histórico" da Fatia 3 do roadmap. Desde a Fase 1.1 todo `create`/`cancelar`/`remarcar`/`confirmar`/etc. já grava em `audit_logs` via `AuditRecorder` (contrato do módulo `auditing`) — mas não havia nenhuma forma de *consultar* esse histórico; só existia através de SQL direto no banco. Esta rodada expõe o que já estava sendo coletado, sem nenhuma migração nova.

- **Nova consulta**: `GET /api/v1/audit-log` retorna os 200 registros mais recentes da organização atual, do mais novo para o mais antigo (`AuditLogRepository.findByOrganizationIdOrderByOccurredAtDesc` com `Pageable` fixo — sem paginação de verdade por ora, já que o volume esperado para uma pequena empresa é baixo; se isso mudar, é só trocar o `PageRequest` fixo por parâmetros de página).
- **Resolução do nome de quem fez a ação**: `audit_logs.actor_user_id` é só um UUID; novo contrato público mínimo `identity.UserLookup` (mesmo padrão de `ServiceLookup`/`ClientLookup`/`BlockLookup`) resolve para o nome de exibição. Ação sem usuária (nenhuma hoje, mas o campo é opcional no schema) aparece como "Sistema"; usuária removida (não deveria acontecer, já que não há exclusão de usuária, mas o código trata o caso) aparece como "Usuária removida".
- **Ajuste de visibilidade interna**: `AuditLogRepository` era pacote-privado (só usado por `JpaAuditRecorder`, no mesmo pacote); precisou virar público para o novo `AuditTrailService` (em `auditing.application`, um sub-pacote diferente) conseguir usá-lo — mesmo módulo, mas Java não permite acesso pacote-privado entre sub-pacotes diferentes, só o Spring Modulith que trata "mesmo módulo" de forma mais ampla que "mesmo pacote Java".
- **Metadados exibidos genericamente**: em vez de formatar cada ação de forma específica, os metadados (`Map<String,String>`, ex.: `reason`, `previousStartAt`/`newStartAt`) aparecem como pares chave/valor crus — simples e já cobre os casos que existem hoje, sem precisar de uma view por tipo de ação.

Painel: nova página "Auditoria" (novo item de navegação), listando cada registro com rótulo amigável da ação (ex.: "Agendamento cancelado" em vez de `APPOINTMENT_CANCELLED`), tipo de entidade, quem fez, quando e os metadados quando existirem. Ações sem rótulo amigável cadastrado mostram o nome cru (fallback seguro para ações futuras que ainda não tenham entrado no mapa de rótulos).

Validado com Postgres real: suíte completa do backend (`./mvnw clean verify`), incluindo `ArchitectureTest` (o acesso `auditing` → `identity.UserLookup` não viola limites do Spring Modulith), isolamento multiempresa e resolução de metadados; suíte do frontend (`ng test`).

## Revisão de qualidade de código (pós Feature 000)

Pausa de manutenção pedida explicitamente ("reavalie com calma") depois de várias rodadas seguidas de funcionalidade nova, cobrindo seis frentes: código morto, duplicação, testes faltantes, magic numbers, módulos grandes demais — com a regra explícita de nunca mudar comportamento sem teste cobrindo a mudança.

- **Código morto**: nenhum encontrado (sem `TODO`/`FIXME`/`XXX` no backend ou frontend, sem métodos ou classes não referenciados).
- **Duplicação**: os seis testes de controller com sessão autenticada (`ClientControllerTest`, `AppointmentControllerTest`, `ServiceControllerTest`, `AvailabilityControllerTest`, `AuditTrailControllerTest`, `DashboardControllerTest`) repetiam, quase sem variação, a lógica de criar usuária + organização + login + obter cookie CSRF (cada um com seu próprio `record AuthenticatedSession`/`Organization` e métodos privados idênticos). Extraído para `br.com.agendaplatform.support.IntegrationTestSupport` (classe utilitária estática, parâmetros explícitos — sem estado compartilhado entre testes). Deliberadamente **não** tocado: o container Testcontainers e `@DynamicPropertySource` continuam duplicados por classe, porque uma base class com container estático mudaria o isolamento "um Postgres por classe de teste" — risco maior que o benefício de deduplicar.
- **Testes faltantes**: `AppointmentTest` (novo, 52 casos, domínio puro sem Spring/Postgres) cobre cada transição de status válida e cada rejeição a partir de um status incompatível via `@ParameterizedTest`/`@EnumSource` — vários caminhos inválidos (ex.: cancelar um `NO_SHOW`, registrar falta a partir de `ARRIVED`, concluir a partir de `SCHEDULED`) nunca tinham sido exercitados por nenhum teste existente. `ClientTest` (novo, 6 casos) cobre a normalização de telefone e as validações do construtor de `Client`, que só eram testadas indiretamente via `ClientControllerTest`. Mais um teste em `AvailabilityControllerTest` (`rejectsBlockWithEndNotAfterStart`) fechando uma assimetria: a mesma validação em `BusinessHours` já era testada, a de `Block` não.
- **Magic numbers**: nenhum encontrado que justificasse extração — constantes existentes (ex.: `AuditTrailService.MAX_ENTRIES`) já são nomeadas.
- **Módulos grandes demais**: `AppointmentScheduler` (261 linhas) é a maior classe do backend, por uma margem real sobre a segunda (`Appointment`, 167 linhas) — mas todas as suas responsabilidades (criar, remarcar, cancelar, as 5 transições de status, 3 consultas) giram em torno do mesmo agregado e já compartilham um único mapeador (`toSummary`). Avaliado e **não dividido** nesta rodada: separar em serviços de comando/consulta juntaria acoplamento novo (ambos dependeriam do mesmo `toSummary`, `clientLookup`, `serviceLookup`) sem remover complexidade de verdade — dividir só para reduzir contagem de linhas seria a abstração prematura que o próprio pedido de revisão queria evitar. No frontend, `agenda-page.component.ts` (246 linhas, maior arquivo do painel) foi avaliado com a mesma conclusão na rodada anterior.

Validado com Postgres real: suíte completa do backend (`./mvnw clean verify`) rodando sem nenhuma mudança de comportamento — os seis arquivos de teste refatorados passam exatamente como antes da extração, mais os 58 testes novos (52 + 6) e o teste adicional de `Block`.

## Fase 1.1 — Validação e fechamento da fundação técnica

Concluída em 2026-07-21 com ressalvas. Ver `docs/qa/foundation-validation.md` para o detalhamento completo.

Resumo das correções aplicadas nesta fase (nenhuma é funcionalidade de negócio):

- Maven Wrapper gerado (`apps/api/mvnw`, `mvnw.cmd`, `.mvn/wrapper/`); scripts, CI e docs migrados de `mvn` para `./mvnw`.
- `pom.xml`: artifactIds do Testcontainers corrigidos (`testcontainers-junit-jupiter`, `testcontainers-postgresql`) — o build nem compilava antes.
- `pom.xml`: dependência do Flyway trocada de `flyway-core` para `spring-boot-starter-flyway` — no Spring Boot 4.1 o autoconfigure do Flyway saiu do jar monolítico e precisa do novo starter; sem isso o Flyway nunca rodava e o app subia silenciosamente sem nenhuma tabela.
- `compose.yaml`: volume do Postgres corrigido para `/var/lib/postgresql` (Postgres 18+ mudou a convenção; o mount antigo em `/var/lib/postgresql/data` deixava o container preso em `unhealthy`).
- `apps/admin/tsconfig.json`: removidas opções depreciadas (`baseUrl`, `downlevelIteration`) que quebravam a compilação com o TypeScript instalado.
- `apps/admin/package.json`: `temporal-polyfill` atualizado para `^1.0.1` para satisfazer o peer dependency do FullCalendar 7 (`npm install` falhava com `ERESOLVE`).
- `apps/admin/package-lock.json` gerado e versionado; CI e `apps/admin/Dockerfile` passaram a usar `npm ci`.
- Repositório Git inicializado nesta fase.

Ressalvas que continuam abertas: ambiente local sem `JAVA_HOME` de sistema em JDK 25 (contorno documentado), ausência de script `npm run lint`, 4 vulnerabilidades npm em tooling de desenvolvimento (não no bundle de produção), Dockerfiles não construídos nesta rodada. Detalhes em `docs/qa/foundation-validation.md`.
