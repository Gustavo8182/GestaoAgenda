ALTER TABLE services
    ADD COLUMN buffer_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE services
    ADD CONSTRAINT services_buffer_minutes_check CHECK (buffer_minutes >= 0);

-- O intervalo é gravado em cada agendamento (cópia do valor do serviço no momento da
-- criação) para que a própria constraint de exclusão abaixo possa usá-lo — diferente do
-- conflito agendamento x bloqueio (ADR 0006), aqui não há junção entre tabelas: o intervalo
-- já está na mesma linha, então dá para proteger de verdade no banco, não só na aplicação.
ALTER TABLE appointments
    ADD COLUMN buffer_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_buffer_minutes_check CHECK (buffer_minutes >= 0);

-- Expressões de índice GiST precisam ser IMMUTABLE; "timestamptz + interval" é marcada
-- STABLE pelo Postgres (mesmo sendo determinística para um intervalo só em minutos, sem
-- meses/anos, cuja duração varia). Contorna somando segundos via epoch — sempre UTC,
-- sem qualquer dependência de fuso horário de sessão, portanto genuinamente IMMUTABLE.
CREATE FUNCTION add_minutes_to_timestamp(ts TIMESTAMPTZ, minutes INTEGER)
    RETURNS TIMESTAMPTZ AS $$
        SELECT to_timestamp(extract(epoch FROM ts) + (minutes * 60));
    $$ LANGUAGE SQL IMMUTABLE STRICT;

ALTER TABLE appointments DROP CONSTRAINT appointments_no_overlap;

ALTER TABLE appointments ADD CONSTRAINT appointments_no_overlap EXCLUDE USING gist (
    organization_id WITH =,
    tstzrange(start_at, add_minutes_to_timestamp(end_at, buffer_minutes)) WITH &&
) WHERE (status NOT IN ('CANCELLED', 'NO_SHOW'));
