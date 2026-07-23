CREATE TABLE relationship_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    origin VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW_CONTACT',
    last_interaction_at TIMESTAMPTZ NOT NULL,
    next_action VARCHAR(255) NULL,
    next_action_at TIMESTAMPTZ NULL,
    responsible_user_id UUID NOT NULL REFERENCES users(id),
    client_id UUID NULL REFERENCES clients(id),
    appointment_id UUID NULL REFERENCES appointments(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT relationship_contacts_status_check CHECK (status IN (
        'NEW_CONTACT', 'IN_SERVICE', 'AWAITING_RESPONSE', 'PENDING_APPOINTMENT', 'SCHEDULED',
        'FOLLOW_UP_LATER', 'DID_NOT_SCHEDULE', 'DO_NOT_CONTACT'))
);

CREATE INDEX relationship_contacts_organization_status_idx ON relationship_contacts (organization_id, status);
