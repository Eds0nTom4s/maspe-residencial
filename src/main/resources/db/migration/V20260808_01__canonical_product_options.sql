-- Canonical product options for CONSUMA AQUI Android V1.
-- Legacy variacoes_produto remains untouched: it has no trustworthy group/selection semantics.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_produtos_tenant_id_id') THEN
        ALTER TABLE produtos ADD CONSTRAINT uq_produtos_tenant_id_id UNIQUE (tenant_id, id);
    END IF;
END $$;

CREATE TABLE product_option_groups (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id BIGINT NOT NULL,
    produto_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    min_selections INTEGER NOT NULL DEFAULT 0,
    max_selections INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_product_option_groups_public_id UNIQUE (public_id),
    CONSTRAINT uq_product_option_groups_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_product_option_groups_min_nonnegative CHECK (min_selections >= 0),
    CONSTRAINT chk_product_option_groups_max_positive CHECK (max_selections >= 1),
    CONSTRAINT chk_product_option_groups_min_max CHECK (min_selections <= max_selections),
    CONSTRAINT chk_product_option_groups_sort_nonnegative CHECK (sort_order >= 0),
    CONSTRAINT fk_product_option_groups_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_product_option_groups_product_tenant FOREIGN KEY (tenant_id, produto_id)
        REFERENCES produtos (tenant_id, id)
);

CREATE TABLE product_options (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id BIGINT NOT NULL,
    option_group_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    additional_price NUMERIC(10, 2) NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    default_selected BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_product_options_public_id UNIQUE (public_id),
    CONSTRAINT uq_product_options_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_product_options_price_nonnegative CHECK (additional_price >= 0),
    CONSTRAINT chk_product_options_sort_nonnegative CHECK (sort_order >= 0),
    CONSTRAINT fk_product_options_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_product_options_group_tenant FOREIGN KEY (tenant_id, option_group_id)
        REFERENCES product_option_groups (tenant_id, id)
);

CREATE INDEX idx_product_option_groups_tenant_product_active_order
    ON product_option_groups (tenant_id, produto_id, active, sort_order, public_id);
CREATE INDEX idx_product_option_groups_tenant_public_id
    ON product_option_groups (tenant_id, public_id);
CREATE INDEX idx_product_options_tenant_group_active_order
    ON product_options (tenant_id, option_group_id, active, sort_order, public_id);
CREATE INDEX idx_product_options_tenant_public_id
    ON product_options (tenant_id, public_id);

DROP TRIGGER IF EXISTS trg_product_option_groups_public_id_immutable ON product_option_groups;
CREATE TRIGGER trg_product_option_groups_public_id_immutable
BEFORE UPDATE OF public_id ON product_option_groups
FOR EACH ROW EXECUTE FUNCTION reject_public_id_update();

DROP TRIGGER IF EXISTS trg_product_options_public_id_immutable ON product_options;
CREATE TRIGGER trg_product_options_public_id_immutable
BEFORE UPDATE OF public_id ON product_options
FOR EACH ROW EXECUTE FUNCTION reject_public_id_update();
