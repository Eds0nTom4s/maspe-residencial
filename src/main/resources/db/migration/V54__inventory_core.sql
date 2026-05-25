-- Prompt 44 — Inventário, stock, COGS e margem (core operacional)

CREATE TABLE IF NOT EXISTS unit_of_measure (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(40) NOT NULL,
    decimal_allowed BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS unit_of_measure
    ADD CONSTRAINT fk_uom_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_uom_tenant_code
    ON unit_of_measure (tenant_id, code);

CREATE INDEX IF NOT EXISTS idx_uom_code
    ON unit_of_measure (code);

CREATE TABLE IF NOT EXISTS unit_conversions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NULL,
    from_unit_id BIGINT NOT NULL,
    to_unit_id BIGINT NOT NULL,
    factor NUMERIC(19, 8) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS unit_conversions
    ADD CONSTRAINT fk_unit_conv_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS unit_conversions
    ADD CONSTRAINT fk_unit_conv_from_unit
        FOREIGN KEY (from_unit_id) REFERENCES unit_of_measure(id);

ALTER TABLE IF EXISTS unit_conversions
    ADD CONSTRAINT fk_unit_conv_to_unit
        FOREIGN KEY (to_unit_id) REFERENCES unit_of_measure(id);

ALTER TABLE IF EXISTS unit_conversions
    ADD CONSTRAINT chk_unit_conv_factor
        CHECK (factor > 0);

CREATE UNIQUE INDEX IF NOT EXISTS uk_unit_conv_tenant_from_to
    ON unit_conversions (tenant_id, from_unit_id, to_unit_id);

CREATE INDEX IF NOT EXISTS idx_unit_conv_from
    ON unit_conversions (from_unit_id);

CREATE INDEX IF NOT EXISTS idx_unit_conv_to
    ON unit_conversions (to_unit_id);

CREATE TABLE IF NOT EXISTS tenant_inventory_policies (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    stock_control_enabled BOOLEAN NOT NULL DEFAULT true,
    allow_negative_stock_default BOOLEAN NOT NULL DEFAULT true,
    strict_stock_required_for_sale BOOLEAN NOT NULL DEFAULT false,
    consumption_trigger VARCHAR(60) NOT NULL DEFAULT 'PAYMENT_CONFIRMED',
    require_recipe_for_stocked_products BOOLEAN NOT NULL DEFAULT false,
    use_average_cost BOOLEAN NOT NULL DEFAULT true,
    margin_calculation_basis VARCHAR(60) NOT NULL DEFAULT 'NET_REVENUE_EXCLUDING_TAX',
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD CONSTRAINT fk_tenant_inventory_policy_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_inventory_policy_one_per_tenant
    ON tenant_inventory_policies (tenant_id);

CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    unidade_atendimento_id BIGINT NULL,
    name VARCHAR(200) NOT NULL,
    sku VARCHAR(80) NULL,
    type VARCHAR(60) NOT NULL,
    category VARCHAR(120) NULL,
    base_unit_id BIGINT NOT NULL,
    stock_control_enabled BOOLEAN NOT NULL DEFAULT true,
    allow_negative_stock BOOLEAN NOT NULL DEFAULT true,
    current_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    average_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    last_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    minimum_quantity NUMERIC(19, 6) NULL,
    reorder_quantity NUMERIC(19, 6) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS inventory_items
    ADD CONSTRAINT fk_inventory_item_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS inventory_items
    ADD CONSTRAINT fk_inventory_item_unidade
        FOREIGN KEY (unidade_atendimento_id) REFERENCES unidades_atendimento(id);

ALTER TABLE IF EXISTS inventory_items
    ADD CONSTRAINT fk_inventory_item_base_unit
        FOREIGN KEY (base_unit_id) REFERENCES unit_of_measure(id);

ALTER TABLE IF EXISTS inventory_items
    ADD CONSTRAINT chk_inventory_item_current_qty
        CHECK (current_quantity IS NOT NULL);

ALTER TABLE IF EXISTS inventory_items
    ADD CONSTRAINT chk_inventory_item_costs
        CHECK (average_cost >= 0 AND last_cost >= 0);

CREATE INDEX IF NOT EXISTS idx_inventory_item_tenant_status
    ON inventory_items (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_inventory_item_tenant_type
    ON inventory_items (tenant_id, type);

CREATE INDEX IF NOT EXISTS idx_inventory_item_tenant_sku
    ON inventory_items (tenant_id, sku);

CREATE TABLE IF NOT EXISTS inventory_recipes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    yield_quantity NUMERIC(19, 6) NOT NULL DEFAULT 1,
    yield_unit_id BIGINT NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NULL,
    effective_to TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS inventory_recipes
    ADD CONSTRAINT fk_inventory_recipe_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS inventory_recipes
    ADD CONSTRAINT fk_inventory_recipe_product
        FOREIGN KEY (product_id) REFERENCES produtos(id);

ALTER TABLE IF EXISTS inventory_recipes
    ADD CONSTRAINT fk_inventory_recipe_yield_unit
        FOREIGN KEY (yield_unit_id) REFERENCES unit_of_measure(id);

ALTER TABLE IF EXISTS inventory_recipes
    ADD CONSTRAINT chk_inventory_recipe_yield_qty
        CHECK (yield_quantity > 0);

CREATE INDEX IF NOT EXISTS idx_inventory_recipe_tenant_product
    ON inventory_recipes (tenant_id, product_id);

CREATE INDEX IF NOT EXISTS idx_inventory_recipe_tenant_status
    ON inventory_recipes (tenant_id, status);

CREATE TABLE IF NOT EXISTS inventory_recipe_lines (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    inventory_item_id BIGINT NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    unit_id BIGINT NOT NULL,
    waste_percentage NUMERIC(9, 4) NOT NULL DEFAULT 0,
    cost_snapshot NUMERIC(19, 6) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS inventory_recipe_lines
    ADD CONSTRAINT fk_inventory_recipe_line_recipe
        FOREIGN KEY (recipe_id) REFERENCES inventory_recipes(id);

ALTER TABLE IF EXISTS inventory_recipe_lines
    ADD CONSTRAINT fk_inventory_recipe_line_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS inventory_recipe_lines
    ADD CONSTRAINT fk_inventory_recipe_line_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);

ALTER TABLE IF EXISTS inventory_recipe_lines
    ADD CONSTRAINT fk_inventory_recipe_line_unit
        FOREIGN KEY (unit_id) REFERENCES unit_of_measure(id);

ALTER TABLE IF EXISTS inventory_recipe_lines
    ADD CONSTRAINT chk_inventory_recipe_line_qty
        CHECK (quantity > 0);

ALTER TABLE IF EXISTS inventory_recipe_lines
    ADD CONSTRAINT chk_inventory_recipe_line_waste
        CHECK (waste_percentage >= 0 AND waste_percentage <= 100);

CREATE INDEX IF NOT EXISTS idx_inventory_recipe_lines_recipe
    ON inventory_recipe_lines (recipe_id);

CREATE INDEX IF NOT EXISTS idx_inventory_recipe_lines_item
    ON inventory_recipe_lines (tenant_id, inventory_item_id);

CREATE TABLE IF NOT EXISTS product_inventory_mappings (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    inventory_item_id BIGINT NULL,
    recipe_id BIGINT NULL,
    stock_policy VARCHAR(60) NOT NULL DEFAULT 'NO_STOCK_CONTROL',
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS product_inventory_mappings
    ADD CONSTRAINT fk_prod_inv_map_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS product_inventory_mappings
    ADD CONSTRAINT fk_prod_inv_map_product
        FOREIGN KEY (product_id) REFERENCES produtos(id);

ALTER TABLE IF EXISTS product_inventory_mappings
    ADD CONSTRAINT fk_prod_inv_map_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);

ALTER TABLE IF EXISTS product_inventory_mappings
    ADD CONSTRAINT fk_prod_inv_map_recipe
        FOREIGN KEY (recipe_id) REFERENCES inventory_recipes(id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_prod_inv_map_tenant_product
    ON product_inventory_mappings (tenant_id, product_id);

CREATE INDEX IF NOT EXISTS idx_prod_inv_map_tenant_policy
    ON product_inventory_mappings (tenant_id, stock_policy);

CREATE TABLE IF NOT EXISTS inventory_consumption_records (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    pedido_id BIGINT NOT NULL,
    pagamento_id BIGINT NULL,
    status VARCHAR(40) NOT NULL,
    trigger_type VARCHAR(60) NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NULL,
    gross_revenue_amount NUMERIC(19, 2) NULL,
    net_revenue_amount NUMERIC(19, 2) NULL,
    tax_amount NUMERIC(19, 2) NULL,
    total_cost NUMERIC(19, 2) NULL,
    estimated_margin_amount NUMERIC(19, 2) NULL,
    estimated_margin_percentage NUMERIC(19, 6) NULL,
    warning_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS inventory_consumption_records
    ADD CONSTRAINT fk_inv_consumption_record_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS inventory_consumption_records
    ADD CONSTRAINT fk_inv_consumption_record_pedido
        FOREIGN KEY (pedido_id) REFERENCES pedidos(id);

ALTER TABLE IF EXISTS inventory_consumption_records
    ADD CONSTRAINT fk_inv_consumption_record_pagamento
        FOREIGN KEY (pagamento_id) REFERENCES pagamentos_gateway(id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_inv_consumption_record_tenant_pedido
    ON inventory_consumption_records (tenant_id, pedido_id);

CREATE INDEX IF NOT EXISTS idx_inv_consumption_record_tenant_status
    ON inventory_consumption_records (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_inv_consumption_record_tenant_created
    ON inventory_consumption_records (tenant_id, created_at);

CREATE TABLE IF NOT EXISTS inventory_consumption_lines (
    id BIGSERIAL PRIMARY KEY,
    consumption_record_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    pedido_item_id BIGINT NULL,
    product_id BIGINT NULL,
    inventory_item_id BIGINT NOT NULL,
    recipe_id BIGINT NULL,
    quantity_consumed NUMERIC(19, 6) NOT NULL,
    unit_id BIGINT NOT NULL,
    quantity_base_unit NUMERIC(19, 6) NOT NULL,
    unit_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    total_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    stock_before NUMERIC(19, 6) NULL,
    stock_after NUMERIC(19, 6) NULL,
    warning_code VARCHAR(120) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS inventory_consumption_lines
    ADD CONSTRAINT fk_inv_consumption_line_record
        FOREIGN KEY (consumption_record_id) REFERENCES inventory_consumption_records(id);

ALTER TABLE IF EXISTS inventory_consumption_lines
    ADD CONSTRAINT fk_inv_consumption_line_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS inventory_consumption_lines
    ADD CONSTRAINT fk_inv_consumption_line_itempedido
        FOREIGN KEY (pedido_item_id) REFERENCES itens_pedido(id);

ALTER TABLE IF EXISTS inventory_consumption_lines
    ADD CONSTRAINT fk_inv_consumption_line_product
        FOREIGN KEY (product_id) REFERENCES produtos(id);

ALTER TABLE IF EXISTS inventory_consumption_lines
    ADD CONSTRAINT fk_inv_consumption_line_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);

ALTER TABLE IF EXISTS inventory_consumption_lines
    ADD CONSTRAINT fk_inv_consumption_line_recipe
        FOREIGN KEY (recipe_id) REFERENCES inventory_recipes(id);

ALTER TABLE IF EXISTS inventory_consumption_lines
    ADD CONSTRAINT fk_inv_consumption_line_unit
        FOREIGN KEY (unit_id) REFERENCES unit_of_measure(id);

ALTER TABLE IF EXISTS inventory_consumption_lines
    ADD CONSTRAINT chk_inv_consumption_line_qty
        CHECK (quantity_consumed > 0 AND quantity_base_unit > 0);

CREATE INDEX IF NOT EXISTS idx_inv_consumption_line_record
    ON inventory_consumption_lines (consumption_record_id);

CREATE INDEX IF NOT EXISTS idx_inv_consumption_line_item
    ON inventory_consumption_lines (tenant_id, inventory_item_id);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    unidade_atendimento_id BIGINT NULL,
    inventory_item_id BIGINT NOT NULL,
    movement_type VARCHAR(60) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    unit_id BIGINT NOT NULL,
    quantity_base_unit NUMERIC(19, 6) NOT NULL,
    unit_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    total_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    stock_before NUMERIC(19, 6) NULL,
    stock_after NUMERIC(19, 6) NULL,
    average_cost_before NUMERIC(19, 6) NULL,
    average_cost_after NUMERIC(19, 6) NULL,
    reference_type VARCHAR(60) NOT NULL,
    reference_id BIGINT NULL,
    source VARCHAR(40) NOT NULL,
    reason TEXT NULL,
    created_by_user_id BIGINT NULL,
    operational_device_id BIGINT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS inventory_movements
    ADD CONSTRAINT fk_inv_movement_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS inventory_movements
    ADD CONSTRAINT fk_inv_movement_unidade
        FOREIGN KEY (unidade_atendimento_id) REFERENCES unidades_atendimento(id);

ALTER TABLE IF EXISTS inventory_movements
    ADD CONSTRAINT fk_inv_movement_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);

ALTER TABLE IF EXISTS inventory_movements
    ADD CONSTRAINT fk_inv_movement_unit
        FOREIGN KEY (unit_id) REFERENCES unit_of_measure(id);

ALTER TABLE IF EXISTS inventory_movements
    ADD CONSTRAINT fk_inv_movement_user
        FOREIGN KEY (created_by_user_id) REFERENCES users(id);

ALTER TABLE IF EXISTS inventory_movements
    ADD CONSTRAINT fk_inv_movement_device
        FOREIGN KEY (operational_device_id) REFERENCES dispositivos_operacionais(id);

ALTER TABLE IF EXISTS inventory_movements
    ADD CONSTRAINT chk_inv_movement_qty
        CHECK (quantity > 0 AND quantity_base_unit > 0);

ALTER TABLE IF EXISTS inventory_movements
    ADD CONSTRAINT chk_inv_movement_costs
        CHECK (unit_cost >= 0 AND total_cost >= 0);

CREATE INDEX IF NOT EXISTS idx_inv_movement_tenant_created
    ON inventory_movements (tenant_id, created_at);

CREATE INDEX IF NOT EXISTS idx_inv_movement_tenant_item
    ON inventory_movements (tenant_id, inventory_item_id);

CREATE INDEX IF NOT EXISTS idx_inv_movement_tenant_type
    ON inventory_movements (tenant_id, movement_type);

CREATE INDEX IF NOT EXISTS idx_inv_movement_tenant_ref
    ON inventory_movements (tenant_id, reference_type, reference_id);

