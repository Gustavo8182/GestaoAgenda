CREATE TABLE waitlist_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    client_id UUID NOT NULL REFERENCES clients(id),
    service_id UUID NOT NULL REFERENCES services(id),
    preferred_start_date DATE NOT NULL,
    preferred_end_date DATE NOT NULL,
    preferred_start_time TIME NOT NULL,
    preferred_end_time TIME NOT NULL,
    priority VARCHAR(10) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    appointment_id UUID NULL REFERENCES appointments(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT waitlist_entries_date_range_check CHECK (preferred_end_date >= preferred_start_date),
    CONSTRAINT waitlist_entries_time_range_check CHECK (preferred_end_time > preferred_start_time),
    CONSTRAINT waitlist_entries_priority_check CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
    -- 'EXPIRED' nunca é persistido: é calculado em tempo de leitura comparando expires_at
    -- com o relógio atual, para não depender de um job agendado que mantenha isso em dia.
    CONSTRAINT waitlist_entries_status_check CHECK (status IN ('WAITING', 'CONVERTED', 'CANCELLED'))
);

CREATE INDEX waitlist_entries_organization_status_idx ON waitlist_entries (organization_id, status);
