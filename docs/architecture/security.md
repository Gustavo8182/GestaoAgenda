# Segurança

## Autenticação planejada

- e-mail e senha;
- senha com algoritmo adaptativo suportado pelo Spring Security;
- sessão persistida;
- cookie HttpOnly, Secure em produção e SameSite;
- CSRF ativo;
- recuperação de senha com token de uso único e expiração.

## Autorização

- proprietária;
- secretária;
- suporte restrito e auditado;
- menor privilégio;
- negação por padrão.

## Dados

- evitar dados de saúde;
- observações exclusivamente administrativas;
- logs técnicos sem dados pessoais completos;
- exportações auditadas;
- backups e restauração testada;
- retenção e exclusão definidas contratualmente.

## Bootstrap atual

A API libera somente status e health. Os demais endpoints estão protegidos, mas o fluxo de login ainda não foi implementado. Não abrir endpoints de negócio para contornar essa ausência.
