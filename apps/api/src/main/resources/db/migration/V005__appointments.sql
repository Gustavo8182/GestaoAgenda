CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    client_id UUID NOT NULL REFERENCES clients(id),
    service_id UUID NOT NULL REFERENCES services(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT appointments_time_range_check CHECK (end_at > start_at),
    -- Uma única agenda por organização (ADR 0002): nenhum agendamento pode se sobrepor
    -- a outro da mesma organização, independente de cliente ou serviço. Usa btree_gist
    -- (habilitada na V001) para permitir "=" e "&&" no mesmo índice de exclusão.
    CONSTRAINT appointments_no_overlap EXCLUDE USING gist (
        organization_id WITH =,
        tstzrange(start_at, end_at) WITH &&
    )
);

CREATE INDEX appointments_organization_start_idx ON appointments (organization_id, start_at);
