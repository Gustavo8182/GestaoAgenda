ALTER TABLE clients
    ADD COLUMN contact_restricted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN contact_restriction_reason TEXT;
