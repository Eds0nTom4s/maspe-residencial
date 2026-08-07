-- Explicit opt-in for the canonical CONSUMA AQUI public Discovery.
-- Existing and new merchants remain private until an internal management flow enables publication.

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS discovery_published boolean;
UPDATE tenants SET discovery_published = false WHERE discovery_published IS NULL;
ALTER TABLE tenants ALTER COLUMN discovery_published SET DEFAULT false;
ALTER TABLE tenants ALTER COLUMN discovery_published SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tenants_discovery_name_public
    ON tenants (lower(nome), merchant_public_id)
    WHERE discovery_published = true AND estado = 'ATIVO';
