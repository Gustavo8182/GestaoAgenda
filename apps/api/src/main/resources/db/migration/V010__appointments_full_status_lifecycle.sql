ALTER TABLE appointments DROP CONSTRAINT appointments_status_check;

ALTER TABLE appointments ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'DONE', 'CANCELLED', 'NO_SHOW'));

-- Só cancelamento e falta liberam o horário; confirmado/chegou/em atendimento/realizado
-- continuam ocupando o horário para efeito de conflito (ver ADR 0006).
ALTER TABLE appointments DROP CONSTRAINT appointments_no_overlap;

ALTER TABLE appointments ADD CONSTRAINT appointments_no_overlap EXCLUDE USING gist (
    organization_id WITH =,
    tstzrange(start_at, end_at) WITH &&
) WHERE (status NOT IN ('CANCELLED', 'NO_SHOW'));
