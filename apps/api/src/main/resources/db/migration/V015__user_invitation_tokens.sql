CREATE TABLE user_invitation_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    -- Nunca guarda o token em texto puro: um vazamento do banco não deve permitir
    -- aceitar um convite de ninguém sem o token original.
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX user_invitation_tokens_user_id_idx ON user_invitation_tokens (user_id);
