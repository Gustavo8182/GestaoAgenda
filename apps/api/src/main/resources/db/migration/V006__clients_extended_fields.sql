ALTER TABLE clients
    ADD COLUMN alternate_phone VARCHAR(40),
    ADD COLUMN alternate_phone_normalized VARCHAR(20),
    ADD COLUMN origin VARCHAR(200),
    ADD COLUMN notes TEXT;

CREATE INDEX clients_alternate_phone_normalized_idx ON clients (organization_id, alternate_phone_normalized);
