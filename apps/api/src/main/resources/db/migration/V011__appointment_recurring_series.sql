ALTER TABLE appointments ADD COLUMN series_id UUID NULL;

-- Só agrupa ocorrências da mesma série para consulta futura (ex.: cancelar a série toda);
-- não é chave estrangeira porque não há uma tabela de série própria — cada ocorrência já é
-- um agendamento completo e independente.
CREATE INDEX idx_appointments_series_id ON appointments (series_id) WHERE series_id IS NOT NULL;
