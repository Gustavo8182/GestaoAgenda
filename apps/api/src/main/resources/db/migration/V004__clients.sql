CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(160) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    phone_normalized VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX clients_organization_idx ON clients (organization_id);
CREATE INDEX clients_phone_normalized_idx ON clients (organization_id, phone_normalized);
