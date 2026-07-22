-- Seed exclusivo do perfil "local" (ver application-local.yml), nunca aplicado em produção.
-- Organização de demonstração vinculada à usuária fictícia criada em V900.
INSERT INTO organizations (name, slug, timezone, status)
VALUES ('Organização de demonstração', 'organizacao-demonstracao', 'America/Sao_Paulo', 'ACTIVE')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO organization_members (organization_id, user_id, role, status)
SELECT o.id, u.id, 'OWNER', 'ACTIVE'
FROM organizations o, users u
WHERE o.slug = 'organizacao-demonstracao'
  AND u.email = 'dona@exemplo.test'
ON CONFLICT (organization_id, user_id) DO NOTHING;
