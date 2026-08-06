-- Public identity foundation for CONSUMA AQUI Android V1.
-- Keeps internal bigint primary/foreign keys and adds immutable, opaque UUID identities.
-- PostgreSQL 16 provides gen_random_uuid() natively; no extension is required.

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS merchant_public_id uuid;
ALTER TABLE produtos ADD COLUMN IF NOT EXISTS public_id uuid;
ALTER TABLE categoria_produtos ADD COLUMN IF NOT EXISTS public_id uuid;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS public_id uuid;

UPDATE tenants SET merchant_public_id = gen_random_uuid() WHERE merchant_public_id IS NULL;
UPDATE produtos SET public_id = gen_random_uuid() WHERE public_id IS NULL;
UPDATE categoria_produtos SET public_id = gen_random_uuid() WHERE public_id IS NULL;
UPDATE pedidos SET public_id = gen_random_uuid() WHERE public_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tenants GROUP BY merchant_public_id HAVING count(*) > 1)
       OR EXISTS (SELECT 1 FROM produtos GROUP BY public_id HAVING count(*) > 1)
       OR EXISTS (SELECT 1 FROM categoria_produtos GROUP BY public_id HAVING count(*) > 1)
       OR EXISTS (SELECT 1 FROM pedidos GROUP BY public_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate public UUID generated during Android identity backfill';
    END IF;
END $$;

ALTER TABLE tenants ALTER COLUMN merchant_public_id SET DEFAULT gen_random_uuid();
ALTER TABLE produtos ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
ALTER TABLE categoria_produtos ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
ALTER TABLE pedidos ALTER COLUMN public_id SET DEFAULT gen_random_uuid();

ALTER TABLE tenants ALTER COLUMN merchant_public_id SET NOT NULL;
ALTER TABLE produtos ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE categoria_produtos ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE pedidos ALTER COLUMN public_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_tenants_merchant_public_id') THEN
        ALTER TABLE tenants ADD CONSTRAINT uq_tenants_merchant_public_id UNIQUE (merchant_public_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_produtos_public_id') THEN
        ALTER TABLE produtos ADD CONSTRAINT uq_produtos_public_id UNIQUE (public_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_categoria_produtos_public_id') THEN
        ALTER TABLE categoria_produtos ADD CONSTRAINT uq_categoria_produtos_public_id UNIQUE (public_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_pedidos_public_id') THEN
        ALTER TABLE pedidos ADD CONSTRAINT uq_pedidos_public_id UNIQUE (public_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_categoria_produtos_tenant_id_id') THEN
        ALTER TABLE categoria_produtos
            ADD CONSTRAINT uq_categoria_produtos_tenant_id_id UNIQUE (tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_produtos_tenant_categoria') THEN
        ALTER TABLE produtos
            ADD CONSTRAINT fk_produtos_tenant_categoria
            FOREIGN KEY (tenant_id, categoria_produto_id)
            REFERENCES categoria_produtos (tenant_id, id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_produtos_tenant_public_id ON produtos (tenant_id, public_id);
CREATE INDEX IF NOT EXISTS idx_categoria_produtos_tenant_public_id ON categoria_produtos (tenant_id, public_id);
CREATE INDEX IF NOT EXISTS idx_pedidos_tenant_public_id ON pedidos (tenant_id, public_id);

CREATE OR REPLACE FUNCTION reject_merchant_public_id_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.merchant_public_id IS DISTINCT FROM NEW.merchant_public_id THEN
        RAISE EXCEPTION 'merchant_public_id is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION reject_public_id_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.public_id IS DISTINCT FROM NEW.public_id THEN
        RAISE EXCEPTION 'public_id is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_tenants_merchant_public_id_immutable ON tenants;
CREATE TRIGGER trg_tenants_merchant_public_id_immutable
BEFORE UPDATE OF merchant_public_id ON tenants
FOR EACH ROW EXECUTE FUNCTION reject_merchant_public_id_update();

DROP TRIGGER IF EXISTS trg_produtos_public_id_immutable ON produtos;
CREATE TRIGGER trg_produtos_public_id_immutable
BEFORE UPDATE OF public_id ON produtos
FOR EACH ROW EXECUTE FUNCTION reject_public_id_update();

DROP TRIGGER IF EXISTS trg_categoria_produtos_public_id_immutable ON categoria_produtos;
CREATE TRIGGER trg_categoria_produtos_public_id_immutable
BEFORE UPDATE OF public_id ON categoria_produtos
FOR EACH ROW EXECUTE FUNCTION reject_public_id_update();

DROP TRIGGER IF EXISTS trg_pedidos_public_id_immutable ON pedidos;
CREATE TRIGGER trg_pedidos_public_id_immutable
BEFORE UPDATE OF public_id ON pedidos
FOR EACH ROW EXECUTE FUNCTION reject_public_id_update();
