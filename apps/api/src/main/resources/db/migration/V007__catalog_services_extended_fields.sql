ALTER TABLE services
    ADD COLUMN color VARCHAR(7),
    ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN requires_confirmation BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX services_organization_active_idx ON services (organization_id, active);
