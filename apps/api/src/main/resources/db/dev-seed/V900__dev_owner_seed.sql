-- Seed exclusivo do perfil "local" (ver application-local.yml), nunca aplicado em produção.
-- Usuária fictícia para permitir login manual em ambiente de desenvolvimento.
-- Senha em texto puro: TrocarSenha123! (apenas para uso local, nunca reutilizar).
INSERT INTO users (email, password_hash, display_name, status)
VALUES (
    'dona@exemplo.test',
    '{bcrypt}$2a$10$WNLb9IZ0fmQN7JtlIzPg9uTGKU8LBPd0FyHKyOqSpZFLbTyxGh0Ea',
    'Proprietária de demonstração',
    'ACTIVE'
)
ON CONFLICT DO NOTHING;
