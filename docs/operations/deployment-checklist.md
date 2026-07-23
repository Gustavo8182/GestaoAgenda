# Checklist de deploy (produção)

Complementa `docs/operations/production-readiness.md`. Este documento cobre o que é
puramente técnico/procedimental; decisões de negócio (provedor de banco, política de
retenção, contrato) ficam naquele arquivo.

## Variáveis de ambiente obrigatórias

Além das já usadas em desenvolvimento (`DB_URL`/`DB_USER`/`DB_PASSWORD`, ver
`docs/operations/local-development.md`), produção precisa de:

| Variável | Valor em produção | Por quê |
|---|---|---|
| `SESSION_COOKIE_SECURE` | não precisa definir — já é `true` por padrão fora do perfil `local` | O padrão passou a ser fail-closed (`true`) desde a auditoria de segurança de 2026-07-23; só o perfil `local` (desenvolvimento sem HTTPS) sobrescreve para `false`. Só defina esta variável manualmente se precisar *desativar* (não recomendado fora de HTTPS de verdade). |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | apontando para o banco gerenciado real | Nunca reaproveitar as credenciais de desenvolvimento (`agenda`/`change-me`). |
| `FRONTEND_URL` | URL pública real do painel (ex.: `https://app.seudominio.com`) | Usada para montar o link de redefinição de senha no e-mail; o padrão (`http://localhost:4200`) só serve para desenvolvimento. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | credenciais do provedor de e-mail transacional real (SES, SendGrid, etc.) | Em desenvolvimento local usa Mailpit (sem autenticação, sem TLS); sem essas variáveis em produção, `POST /api/v1/auth/password-reset/request` recebe o e-mail mas nunca consegue enviá-lo — falha silenciosa do ponto de vista de quem pediu a redefinição. |
| `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | `true` (padrão já é `true`) | Só reduzir para `false` caso o provedor de e-mail realmente não use autenticação/TLS — não é o caso da maioria dos provedores de produção. |

## HTTPS

A API não termina TLS sozinha — isso é esperado em produção. Colocar atrás de um
reverse proxy ou load balancer (nginx, um serviço gerenciado de load balancer, etc.) que
termine HTTPS e encaminhe para a API por HTTP dentro da rede privada. Sem isso,
`SESSION_COOKIE_SECURE=true` faz o navegador *nunca* enviar o cookie de sessão (já que a
conexão não seria HTTPS de verdade), quebrando o login.

## Smoke test após deploy

```bash
./scripts/smoke-test.sh https://sua-api-em-producao.exemplo.com
```

```powershell
./scripts/smoke-test.ps1 -BaseUrl "https://sua-api-em-producao.exemplo.com"
```

Confirma três coisas mínimas: a API está de pé (`/actuator/health`), responde
(`/api/v1/system/status`) e nega acesso sem sessão (`/api/v1/auth/me` → 401). Não substitui o
E2E completo (`apps/admin/e2e/critical-flow.spec.ts`, já rodando no CI) — é para rodar contra
o ambiente real depois de cada deploy, quando o E2E completo seria caro/arriscado demais para
rodar direto em produção.

## Backup e restauração

Procedimento com `pg_dump`/`pg_restore` (formato customizado, permite restauração parcial e é
mais rápido que SQL puro):

```bash
# Backup
pg_dump --format=custom --file=backup-$(date +%Y%m%d-%H%M%S).dump \
  --host=<host> --username=<user> --dbname=<db>

# Restauração (banco de destino precisa existir e estar vazio)
pg_restore --host=<host> --username=<user> --dbname=<db> --clean --if-exists backup-<data>.dump
```

**Não testado contra um ambiente real de produção** (ainda não existe) — testar de verdade
significa: gerar um backup do ambiente de homologação, restaurar num banco novo, rodar a suíte
de smoke test contra ele e confirmar que os dados batem. Fazer isso antes do primeiro cliente
real, não só confiar no procedimento escrito.

Frequência e retenção de backups ficam para quando a política de retenção for definida
contratualmente (ver `docs/operations/production-readiness.md`).

## Rollback

As migrações Flyway deste projeto são **só para frente** (não há scripts de "down" versionados
— nenhuma migração até agora precisou disso). Em caso de deploy problemático:

1. **Se a migração de banco não rodou ou é compatível com a versão anterior da API**: reverter
   só a imagem/artefato da API para a tag anterior. Mais simples, mais comum.
2. **Se uma migração de banco quebrou algo e não é seguro continuar**: restaurar o backup mais
   recente anterior ao deploy (ver seção acima) e então reverter a imagem da API. Mais lento,
   usar só se o passo 1 não resolver.
3. Rodar o smoke test (seção acima) contra o ambiente depois de qualquer rollback, antes de
   considerar o incidente encerrado.

Regra prática para reduzir a chance de precisar do passo 2: migrações que alteram uma coluna
usada por uma versão anterior da API em produção (renomear, mudar tipo, tornar `NOT NULL`) só
devem ir para uma release onde a API antiga já não está mais rodando em paralelo — evite
misturar "migração que quebra compatibilidade" com "deploy sem downtime" na mesma release.
