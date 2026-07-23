-- Impede que a mesma usuária tenha vínculo ATIVO com mais de uma organização ao mesmo tempo,
-- reforçando no banco um invariante do qual SecurityCurrentOrganizationProvider já depende
-- (findActiveMembershipByUserId assume no máximo um resultado). Até aqui isso só era evitado
-- pela regra de convite (recusa e-mail já cadastrado em qualquer status/organização); um
-- provisionamento manual direto no banco conseguia violar o invariante e derrubava o login com
-- 500 (IncorrectResultSizeDataAccessException não tratada).
CREATE UNIQUE INDEX organization_members_one_active_per_user
    ON organization_members (user_id)
    WHERE status = 'ACTIVE';
