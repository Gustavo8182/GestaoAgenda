ALTER TABLE appointments
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    ADD COLUMN cancellation_reason TEXT,
    ADD CONSTRAINT appointments_status_check CHECK (status IN ('SCHEDULED', 'CANCELLED'));

-- A constraint original barrava sobreposição considerando TODAS as linhas, inclusive as
-- canceladas — o que travaria para sempre o horário de um agendamento já cancelado.
-- Recriamos como EXCLUDE parcial (WHERE status = 'SCHEDULED') para que cancelar libere o horário.
ALTER TABLE appointments DROP CONSTRAINT appointments_no_overlap;

ALTER TABLE appointments ADD CONSTRAINT appointments_no_overlap EXCLUDE USING gist (
    organization_id WITH =,
    tstzrange(start_at, end_at) WITH &&
) WHERE (status = 'SCHEDULED');
