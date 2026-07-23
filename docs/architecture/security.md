# Segurança

## Autenticação

- e-mail e senha (`DelegatingPasswordEncoder`, bcrypt);
- sessão persistida via `HttpSession` (Spring Security), com registro em `SessionRegistry` (em
  memória — ver "Limitação conhecida" abaixo);
- cookie `HttpOnly`, `Secure` por padrão fora do perfil `local` (fail-closed desde a auditoria de
  segurança de 2026-07-23), `SameSite=Lax`;
- CSRF ativo (`CookieCsrfTokenRepository`);
- proteção contra fixação de sessão: o ID da sessão é trocado no momento em que ela vira
  autenticada (`SessionAuthenticationStrategy`, aplicado manualmente no login customizado);
- recuperação de senha com token de uso único e expiração (hash SHA-256, nunca gravado em texto
  puro) — redefinir a senha revoga todas as sessões ativas da usuária na hora;
- convite de usuária (e-mail com token de uso único, mesmo padrão do reset de senha, validade
  maior) — só a proprietária pode convidar, e só para o papel `SECRETARY`;
- desativar o vínculo de uma usuária com a organização também revoga as sessões ativas dela na
  hora (mesmo mecanismo do reset de senha, `shared.security.SessionRevoker`).

**Limitação conhecida e aceita por ora**: o `SessionRegistry` (usado para revogação de sessão e
proteção de fixação) é em memória, não compartilhado entre instâncias da API. Rodando com mais de
uma instância, a revogação de sessão (reset de senha, desativação de usuária) só afeta a instância
que processou o pedido — sessões registradas em outras instâncias continuam válidas até expirarem
naturalmente. Ativar o Spring Session JDBC (a dependência e a migração `V002` já existem, mas
`@EnableJdbcHttpSession` nunca foi ligado) resolveria isso, mas exigiria adaptar todos os testes de
integração que capturam a sessão via `MockHttpSession` — decisão explícita de deixar para uma
rodada futura, dado que hoje a aplicação roda em instância única.

## Autorização

Por papel (`OrganizationRole`): `OWNER`, `SECRETARY`, `SUPPORT`.

- **OWNER**: acesso total — inclui exportação CSV, criar/inativar serviço, alterar horário de
  funcionamento, gerenciar usuárias (convidar, desativar, reativar).
- **SECRETARY**: opera o dia a dia — clientes, agendamentos (status, cancelamento, remarcação,
  recorrência), lista de espera, relacionamento, bloqueios pontuais da agenda. Sem acesso às ações
  acima restritas à OWNER.
- **SUPPORT**: nenhum acesso operacional hoje, de propósito — acesso técnico de suporte (menor
  privilégio, justificativa, duração limitada, auditoria) será desenhado separadamente, ainda não
  implementado.

Aplicado no backend via `organizations.OrganizationAccessGuard` (`requireOwner()`/
`requireOperator()`, este último nega só `SUPPORT`), chamado como primeira linha de cada método de
aplicação que precisa de restrição — nunca só no frontend. Toda negação de acesso responde 403
(`AccessDeniedException`, capturada pelo filtro de segurança padrão do Spring Security). A UI
esconde as ações restritas como reforço de usabilidade, não como controle de acesso.

**Modelo alinhado para o futuro acesso de SUPPORT** (decisão de 2026-07-23, ainda não
implementada — ver backlog em `docs/product/roadmap.md`): quem pode ter esse papel é só
desenvolvedor(a) responsável hoje, futuramente membros explicitamente autorizados de uma equipe
técnica — nunca clientes comuns. O acesso deve ser excepcional, não permanente: escopado a uma
única organização por vez, preferencialmente somente leitura, com motivo obrigatório, início/fim
registrados e toda ação auditada. Por padrão, SUPPORT não deve conseguir criar/remarcar/cancelar
agendamento, editar cliente, exportar dado, alterar serviço, mudar configuração nem gerenciar
usuárias — essas continuam bloqueadas por `requireOperator()` como já estão hoje. Decisão explícita
de manter simples por ora: nenhum sistema de tickets/aprovação formal nesta fase; prioriza-se
segurança excessiva (SUPPORT sem nenhum acesso de negócio) a acesso amplo prematuro, até que exista
uma equipe de suporte de verdade justificando a complexidade extra.

**Troca de papel de uma usuária existente** (decisão registrada, ainda não implementada — ver
backlog): quando construída, só `OWNER` pode trocar o papel de outro membro entre `SECRETARY` e
`OWNER`. Guarda-corpos obrigatórios: uma organização nunca pode ficar sem nenhuma `OWNER` ativa
(bloquear a troca/desativação da última); toda troca de papel deve ser auditada; `SECRETARY` e
`SUPPORT` nunca podem alterar papéis de ninguém.

## Dados

- evitar dados de saúde;
- observações exclusivamente administrativas;
- logs técnicos sem dados pessoais completos;
- exportações auditadas (`CLIENTS_EXPORTED`, `APPOINTMENTS_EXPORTED`, `WAITLIST_EXPORTED`,
  `RELATIONSHIPS_EXPORTED` — só `OWNER`);
- backups e restauração testada;
- retenção e exclusão definidas contratualmente (fora do escopo técnico — ver
  `docs/operations/production-readiness.md`).

## Superfície administrativa (Actuator)

Só `/actuator/health` é exposto publicamente — usado para health check de infraestrutura.
`/actuator/metrics` e `/actuator/info` não são expostos (removidos de
`management.endpoints.web.exposure.include`) desde a auditoria de segurança de 2026-07-23: antes
disso, qualquer usuária autenticada (qualquer papel) conseguia listar métricas internas da
aplicação.

## Bootstrap atual

A API libera status, health, `POST /api/v1/auth/login`, `POST /api/v1/auth/password-reset/**` e
`POST /api/v1/auth/invitations/accept`. Os demais endpoints exigem sessão autenticada. Cadastro de
usuária por API existe desde a rodada de gestão de usuárias: a proprietária convida por e-mail
(`POST /api/v1/organizations/members`); não há autocadastro nem convite para o papel `OWNER` — a
primeira usuária de cada organização continua vindo do seed de desenvolvimento (perfil `local`) ou
de provisionamento manual em produção.
